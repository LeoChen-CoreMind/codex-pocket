package main

import (
	"bufio"
	"bytes"
	"crypto/rand"
	"crypto/sha1"
	"encoding/base64"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"
)

const (
	bridgeAddress = "127.0.0.1:47831"
	bridgePath    = "/internal/vscode"
	maxFrameBytes = 128 * 1024 * 1024
)

type wsConn struct {
	conn    net.Conn
	reader  *bufio.Reader
	writeMu sync.Mutex
}

func main() {
	args := os.Args[1:]
	if !contains(args, "app-server") {
		exitWith(delegate(args))
	}

	ws := waitForBridge()
	defer ws.conn.Close()

	err := relay(ws)
	if err != nil && !errors.Is(err, io.EOF) && !errors.Is(err, net.ErrClosed) {
		fmt.Fprintf(os.Stderr, "[codex-proxy] Bridge connection closed: %v\n", err)
		os.Exit(1)
	}
}

func waitForBridge() *wsConn {
	for attempt := 0; ; attempt++ {
		ws, err := connectBridge()
		if err == nil {
			if attempt > 0 {
				fmt.Fprintln(os.Stderr, "[codex-proxy] Bridge connected")
			}
			return ws
		}

		// Falling back to another app-server splits desktop and mobile state. Keep
		// the official editor client waiting until the shared Bridge is available.
		if attempt == 0 || attempt%30 == 0 {
			fmt.Fprintf(os.Stderr, "[codex-proxy] Waiting for Bridge at %s: %v\n", bridgeAddress, err)
		}
		time.Sleep(time.Second)
	}
}

func contains(values []string, wanted string) bool {
	for _, value := range values {
		if value == wanted {
			return true
		}
	}
	return false
}

func delegate(args []string) error {
	binaryPath, err := findRealCodex()
	if err != nil {
		return err
	}
	command := exec.Command(binaryPath, args...)
	command.Stdin = os.Stdin
	command.Stdout = os.Stdout
	command.Stderr = os.Stderr
	return command.Run()
}

func findRealCodex() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	pattern := filepath.Join(home, ".vscode", "extensions", "openai.chatgpt-*", "bin", "windows-x86_64", "codex.exe")
	matches, err := filepath.Glob(pattern)
	if err != nil {
		return "", err
	}
	type candidate struct {
		path  string
		mtime time.Time
	}
	candidates := make([]candidate, 0, len(matches))
	for _, path := range matches {
		info, statErr := os.Stat(path)
		if statErr == nil {
			candidates = append(candidates, candidate{path: path, mtime: info.ModTime()})
		}
	}
	if len(candidates) == 0 {
		return "", errors.New("VS Code Codex binary not found")
	}
	sort.Slice(candidates, func(i, j int) bool { return candidates[i].mtime.After(candidates[j].mtime) })
	return candidates[0].path, nil
}

func connectBridge() (*wsConn, error) {
	dialer := net.Dialer{Timeout: time.Second}
	conn, err := dialer.Dial("tcp", bridgeAddress)
	if err != nil {
		return nil, err
	}
	fail := func(reason error) (*wsConn, error) {
		conn.Close()
		return nil, reason
	}
	_ = conn.SetDeadline(time.Now().Add(3 * time.Second))

	keyBytes := make([]byte, 16)
	if _, err = rand.Read(keyBytes); err != nil {
		return fail(err)
	}
	key := base64.StdEncoding.EncodeToString(keyBytes)
	token, err := readToken()
	if err != nil {
		return fail(err)
	}
	request := fmt.Sprintf(
		"GET %s HTTP/1.1\r\nHost: %s\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: %s\r\nSec-WebSocket-Version: 13\r\nAuthorization: Bearer %s\r\n\r\n",
		bridgePath, bridgeAddress, key, token,
	)
	if _, err = io.WriteString(conn, request); err != nil {
		return fail(err)
	}

	reader := bufio.NewReader(conn)
	status, err := reader.ReadString('\n')
	if err != nil {
		return fail(err)
	}
	if !strings.Contains(status, " 101 ") {
		return fail(fmt.Errorf("unexpected handshake status: %s", strings.TrimSpace(status)))
	}
	headers := make(map[string]string)
	for {
		line, readErr := reader.ReadString('\n')
		if readErr != nil {
			return fail(readErr)
		}
		line = strings.TrimSpace(line)
		if line == "" {
			break
		}
		parts := strings.SplitN(line, ":", 2)
		if len(parts) == 2 {
			headers[strings.ToLower(strings.TrimSpace(parts[0]))] = strings.TrimSpace(parts[1])
		}
	}
	digest := sha1.Sum([]byte(key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"))
	expected := base64.StdEncoding.EncodeToString(digest[:])
	if headers["sec-websocket-accept"] != expected {
		return fail(errors.New("invalid WebSocket accept header"))
	}
	_ = conn.SetDeadline(time.Time{})
	return &wsConn{conn: conn, reader: reader}, nil
}

func readToken() (string, error) {
	localAppData := os.Getenv("LOCALAPPDATA")
	if localAppData == "" {
		return "", errors.New("LOCALAPPDATA is not set")
	}
	value, err := os.ReadFile(filepath.Join(localAppData, "CodexMobileBridge", "bridge.token"))
	if err != nil {
		return "", err
	}
	token := strings.TrimSpace(string(value))
	if token == "" {
		return "", errors.New("Bridge token is empty")
	}
	return token, nil
}

func relay(ws *wsConn) error {
	errorsChannel := make(chan error, 2)
	go func() { errorsChannel <- stdinToWebSocket(ws) }()
	go func() { errorsChannel <- webSocketToStdout(ws) }()
	err := <-errorsChannel
	_ = ws.writeFrame(0x8, nil)
	_ = ws.conn.Close()
	return err
}

func stdinToWebSocket(ws *wsConn) error {
	reader := bufio.NewReaderSize(os.Stdin, 64*1024)
	for {
		line, err := reader.ReadBytes('\n')
		line = bytes.TrimRight(line, "\r\n")
		if len(line) > maxFrameBytes {
			return errors.New("JSON-RPC line exceeds size limit")
		}
		if len(line) > 0 {
			if writeErr := ws.writeFrame(0x1, line); writeErr != nil {
				return writeErr
			}
		}
		if err != nil {
			return err
		}
	}
}

func webSocketToStdout(ws *wsConn) error {
	for {
		payload, err := ws.readMessage()
		if err != nil {
			return err
		}
		if _, err = os.Stdout.Write(append(payload, '\n')); err != nil {
			return err
		}
	}
}

func (ws *wsConn) writeFrame(opcode byte, payload []byte) error {
	ws.writeMu.Lock()
	defer ws.writeMu.Unlock()

	header := []byte{0x80 | opcode}
	length := uint64(len(payload))
	switch {
	case length < 126:
		header = append(header, byte(length)|0x80)
	case length <= 0xffff:
		header = append(header, 126|0x80, byte(length>>8), byte(length))
	default:
		header = append(header, 127|0x80)
		lengthBytes := make([]byte, 8)
		binary.BigEndian.PutUint64(lengthBytes, length)
		header = append(header, lengthBytes...)
	}
	mask := make([]byte, 4)
	if _, err := rand.Read(mask); err != nil {
		return err
	}
	header = append(header, mask...)
	masked := make([]byte, len(payload))
	for i := range payload {
		masked[i] = payload[i] ^ mask[i%4]
	}
	if _, err := ws.conn.Write(header); err != nil {
		return err
	}
	_, err := ws.conn.Write(masked)
	return err
}

func (ws *wsConn) readMessage() ([]byte, error) {
	var message bytes.Buffer
	fragmented := false
	for {
		first, err := ws.reader.ReadByte()
		if err != nil {
			return nil, err
		}
		second, err := ws.reader.ReadByte()
		if err != nil {
			return nil, err
		}
		fin := first&0x80 != 0
		opcode := first & 0x0f
		masked := second&0x80 != 0
		length := uint64(second & 0x7f)
		if length == 126 {
			var raw [2]byte
			if _, err = io.ReadFull(ws.reader, raw[:]); err != nil {
				return nil, err
			}
			length = uint64(binary.BigEndian.Uint16(raw[:]))
		} else if length == 127 {
			var raw [8]byte
			if _, err = io.ReadFull(ws.reader, raw[:]); err != nil {
				return nil, err
			}
			length = binary.BigEndian.Uint64(raw[:])
		}
		if length > maxFrameBytes || uint64(message.Len())+length > maxFrameBytes {
			return nil, errors.New("WebSocket frame exceeds size limit")
		}
		var mask [4]byte
		if masked {
			if _, err = io.ReadFull(ws.reader, mask[:]); err != nil {
				return nil, err
			}
		}
		payload := make([]byte, int(length))
		if _, err = io.ReadFull(ws.reader, payload); err != nil {
			return nil, err
		}
		if masked {
			for i := range payload {
				payload[i] ^= mask[i%4]
			}
		}

		switch opcode {
		case 0x8:
			return nil, io.EOF
		case 0x9:
			if err = ws.writeFrame(0xA, payload); err != nil {
				return nil, err
			}
			continue
		case 0xA:
			continue
		case 0x1:
			if fragmented {
				return nil, errors.New("unexpected text frame")
			}
			message.Write(payload)
			fragmented = !fin
		case 0x0:
			if !fragmented {
				return nil, errors.New("unexpected continuation frame")
			}
			message.Write(payload)
			fragmented = !fin
		default:
			return nil, fmt.Errorf("unsupported WebSocket opcode %d", opcode)
		}
		if fin && !fragmented {
			return message.Bytes(), nil
		}
	}
}

func exitWith(err error) {
	if err == nil {
		os.Exit(0)
	}
	var exitError *exec.ExitError
	if errors.As(err, &exitError) {
		os.Exit(exitError.ExitCode())
	}
	fmt.Fprintln(os.Stderr, err)
	os.Exit(1)
}

import Foundation
import Shared

/// Helper to resolve Koin dependencies from Swift.
/// Uses IosKoinAccessor (Kotlin-side) to avoid exposing Koin internals to Swift.
/// Koin is started in iOSApp.init() via IosKoinHelperKt.startIosKoin().
///
/// All accessors use try! because these are startup-time singletons that must
/// resolve. If Koin is misconfigured, we want a clear crash with the Koin error
/// message rather than a mysterious SIGABRT from trapOnUndeclaredException.
enum KoinHelper {

    static var sdk: LibreChatSDK {
        try! IosKoinAccessor.shared.getSDK()
    }

    static var serverDataStore: ServerDataStore {
        try! IosKoinAccessor.shared.getServerDataStore()
    }

    static var authRepository: any AuthRepository {
        try! IosKoinAccessor.shared.getAuthRepository()
    }

    static var fileRepository: any FileRepository {
        try! IosKoinAccessor.shared.getFileRepository()
    }

    static var configRepository: any ConfigRepository {
        try! IosKoinAccessor.shared.getConfigRepository()
    }
}

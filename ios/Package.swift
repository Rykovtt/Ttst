// swift-tools-version:5.9
// Только для проверки логики на Linux/macOS без Xcode: `swift test`.
// Приложение собирается проектом Xcode из project.yml.
import PackageDescription

let package = Package(
    name: "RVaultCore",
    targets: [
        .target(name: "RVaultCore", path: "RVault/Core"),
        .testTarget(name: "CoreTests", dependencies: ["RVaultCore"], path: "Tests/CoreTests"),
    ]
)

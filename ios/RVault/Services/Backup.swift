import CommonCrypto
import CryptoKit
import Foundation
import SwiftUI
import UniformTypeIdentifiers

/// Резервная копия: архив + фото в одном файле, зашифрованном паролем
/// (PBKDF2-SHA256 → AES-256-GCM). Без пароля — просто JSON.
enum Backup {
    struct Payload: Codable {
        var archive: Archive
        var photos: [String: Data]
    }

    enum Failure: LocalizedError {
        case wrongPassword, badFile
        var errorDescription: String? {
            switch self {
            case .wrongPassword: return "Неверный пароль или повреждённый файл"
            case .badFile: return "Это не резервная копия RVault"
            }
        }
    }

    private static let magicEncrypted = Data("RVI1".utf8)
    private static let magicPlain = Data("RVI0".utf8)

    @MainActor
    static func export(password: String) throws -> Data {
        let store = Store.shared
        var photos: [String: Data] = [:]
        for p in store.people {
            for file in p.photos.map(\.file) + [p.avatar].compactMap({ $0 }) {
                if photos[file] == nil, let d = try? Data(contentsOf: store.photoURL(file)) { photos[file] = d }
            }
        }
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .secondsSince1970
        let json = try encoder.encode(Payload(archive: store.archive, photos: photos))
        if password.isEmpty { return magicPlain + json }
        let salt = Data((0..<16).map { _ in UInt8.random(in: 0...255) })
        let sealed = try AES.GCM.seal(json, using: key(password, salt: salt))
        return magicEncrypted + salt + sealed.combined!
    }

    static func decode(_ data: Data, password: String) throws -> Payload {
        let magic = data.prefix(4)
        let json: Data
        if magic == magicPlain {
            json = data.dropFirst(4)
        } else if magic == magicEncrypted {
            let salt = data.dropFirst(4).prefix(16)
            let body = data.dropFirst(20)
            do {
                let box = try AES.GCM.SealedBox(combined: body)
                json = try AES.GCM.open(box, using: key(password, salt: Data(salt)))
            } catch {
                throw Failure.wrongPassword
            }
        } else {
            throw Failure.badFile
        }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .secondsSince1970
        guard let payload = try? decoder.decode(Payload.self, from: Data(json)) else { throw Failure.badFile }
        return payload
    }

    @MainActor
    static func restore(_ payload: Payload, replace: Bool) -> Int {
        let store = Store.shared
        for (name, data) in payload.photos { store.storePhotoData(data, name: name) }
        if replace { store.replaceArchive(payload.archive) } else { store.merge(payload.archive) }
        return payload.archive.people.count
    }

    private static func key(_ password: String, salt: Data) -> SymmetricKey {
        var derived = [UInt8](repeating: 0, count: 32)
        let pass = Array(password.utf8)
        salt.withUnsafeBytes { saltPtr in
            _ = CCKeyDerivationPBKDF(
                CCPBKDFAlgorithm(kCCPBKDF2),
                pass.map { Int8(bitPattern: $0) }, pass.count,
                saltPtr.bindMemory(to: UInt8.self).baseAddress, salt.count,
                CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), 120_000,
                &derived, derived.count
            )
        }
        return SymmetricKey(data: derived)
    }
}

/// Файл копии для системного окна «Сохранить в Файлы».
struct BackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.data] }
    var data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

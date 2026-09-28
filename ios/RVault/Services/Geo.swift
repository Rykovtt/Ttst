import CoreLocation
import Foundation

/// Адрес ↔ координаты через геокодер Apple.
enum Geo {
    static func locate(_ address: String) async -> CLLocationCoordinate2D? {
        guard !address.isBlank else { return nil }
        let marks = try? await CLGeocoder().geocodeAddressString(address)
        return marks?.first?.location?.coordinate
    }

    static func address(of c: CLLocationCoordinate2D) async -> String? {
        let marks = try? await CLGeocoder().reverseGeocodeLocation(CLLocation(latitude: c.latitude, longitude: c.longitude))
        guard let m = marks?.first else { return nil }
        return [m.locality, m.thoroughfare, m.subThoroughfare].compactMap { $0 }.joined(separator: ", ")
    }

    /// Находит координаты для адресов, у которых их ещё нет.
    @MainActor
    static func fillMissing() async {
        let store = Store.shared
        for p in store.people {
            for place in p.places where !place.hasCoords && !place.address.isBlank {
                guard let c = await locate(place.address) else { continue }
                store.update(p.id) { person in
                    if let i = person.places.firstIndex(where: { $0.id == place.id }) {
                        person.places[i].lat = c.latitude
                        person.places[i].lng = c.longitude
                    }
                }
            }
        }
    }
}

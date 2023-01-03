import Foundation


enum TimestampType {
	case milliseconds
	case seconds
}

extension Int64 {
	
	func toDate(from timestampType: TimestampType) -> Date {
		switch timestampType {
			case .milliseconds : return Date(timeIntervalSince1970: TimeInterval(self) / TimeInterval(1_000))
			case .seconds      : return Date(timeIntervalSince1970: TimeInterval(self))
		}
	}
}
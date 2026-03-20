import CallKit
import os.log

/// Observes phone call state via CallKit's CXCallObserver and notifies
/// a delegate when the state changes. Used for live call status sync to watch.
///
/// Only active when "Live Call Status" is enabled in settings.
class CallStateObserver: NSObject {

    private let logger = Logger(subsystem: "com.wearos.ancsbridge", category: "CallStateObserver")
    private let callObserver = CXCallObserver()

    /// Called whenever call state changes. Values match BLEConstants.callState* constants.
    var onCallStateChanged: ((UInt8) -> Void)?

    /// Current call state (for reads)
    private(set) var currentState: UInt8 = BLEConstants.callStateIdle

    private var isObserving = false

    func startObserving() {
        guard !isObserving else { return }
        callObserver.setDelegate(self, queue: nil)
        isObserving = true
        logger.info("Started observing call state")
    }

    func stopObserving() {
        isObserving = false
        currentState = BLEConstants.callStateIdle
        logger.info("Stopped observing call state")
    }
}

extension CallStateObserver: CXCallObserverDelegate {

    func callObserver(_ callObserver: CXCallObserver, callChanged call: CXCall) {
        let newState: UInt8

        if call.hasEnded {
            newState = BLEConstants.callStateEnded
        } else if call.isOutgoing {
            // We only care about incoming calls for the watch overlay
            return
        } else if call.hasConnected {
            // Incoming call was answered
            newState = BLEConstants.callStateActive
        } else {
            // Incoming call is ringing
            newState = BLEConstants.callStateRinging
        }

        guard newState != currentState else { return }
        currentState = newState

        let stateStr: String
        switch newState {
        case BLEConstants.callStateRinging: stateStr = "ringing"
        case BLEConstants.callStateActive: stateStr = "active"
        case BLEConstants.callStateEnded: stateStr = "ended"
        default: stateStr = "idle"
        }
        logger.info("Call state changed: \(stateStr)")

        onCallStateChanged?(newState)

        // Reset to idle after ended, with a short delay
        if newState == BLEConstants.callStateEnded {
            DispatchQueue.main.asyncAfter(deadline: .now() + 2) { [weak self] in
                self?.currentState = BLEConstants.callStateIdle
                self?.onCallStateChanged?(BLEConstants.callStateIdle)
            }
        }
    }
}

package lsoc.gateway.standalone.lbtr.receiver;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("mote_rssi_data")
public class MoteRssiPayload {
    public String macAddress;
    public double rssiSelfToRemote;
    public double rssiRemoteToSelf;

    @JsonCreator
    public MoteRssiPayload(@JsonProperty(value = "mac", required = true) String macAddress,
                           @JsonProperty(value = "rssiToRemote", required = true) double rssiSelfToRemote,
                           @JsonProperty(value = "rssiFromRemote", required = true) double rssiRemoteToSelf) {
        this.macAddress = macAddress;
        this.rssiSelfToRemote = rssiSelfToRemote;
        this.rssiRemoteToSelf = rssiRemoteToSelf;
    }

    public MoteRssiPayload(String macAddress, double rssi) {
        this.macAddress = macAddress;
        this.rssiSelfToRemote = rssi;
        this.rssiRemoteToSelf = rssi;
    }

    public double rssi() {
        return (rssiSelfToRemote + rssiRemoteToSelf) / 2;
    }

    @Override
    public String toString() {
        return String.format("{RSSI [%s]=(to anchor: %.2f, from anchor: %.2f)}", macAddress, rssiSelfToRemote, rssiRemoteToSelf);
    }
}

package lsoc.gateway.standalone.data;

import com.fasterxml.jackson.annotation.JsonRootName;

import java.io.Serializable;
import java.util.*;

@JsonRootName("resource")
public class Resource implements Serializable {
    public String id;
    public Calendar timestamp;
    public String owner;
    public String type;
    public String value;
    public String actions;

    public Set<Action> availableActions() {
        Set<Action> availableActions = new HashSet<>();
        for (String action : actions.split(",")) {
            try {
                availableActions.add(Action.valueOf(action.trim()));
            } catch(IllegalArgumentException ignored) {
            }
        }
        return Collections.unmodifiableSet(availableActions);
    }

    @Override
    public String toString() {
        return String.format("{Resource: %s, owned by %s @ %s %s %s}",
                id, owner, javax.xml.bind.DatatypeConverter.printDateTime(timestamp), availableActions(), value);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Resource))
            return false;
        Resource other = (Resource) obj;
        return other.id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}

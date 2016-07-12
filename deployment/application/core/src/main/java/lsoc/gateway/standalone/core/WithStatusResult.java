package lsoc.gateway.standalone.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import lsoc.gateway.standalone.data.Status;

public class WithStatusResult<T> {
    @JsonProperty
    public Status status;
    @JsonProperty
    public T data;

    public WithStatusResult() {
        this.status = Status.FAILURE;
        this.data = null;
    }

    public WithStatusResult(T data) {
        this.status = Status.SUCCESS;
        this.data = data;
    }
}


package ru.max.bot.model;

import com.fasterxml.jackson.annotation.*;

import javax.annotation.processing.Generated;
import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("org.jsonschema2pojo")
@JsonPropertyOrder({
    "update_id",
    "message"
})
public class Result {

    @JsonProperty("update_id")
    private Integer updateId;
    @JsonProperty("message")
    private Message message;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * 
     * @return
     *     The updateId
     */
    @JsonProperty("update_id")
    public Integer getUpdateId() {
        return updateId;
    }

    /**
     * 
     * @param updateId
     *     The update_id
     */
    @JsonProperty("update_id")
    public void setUpdateId(Integer updateId) {
        this.updateId = updateId;
    }

    /**
     * 
     * @return
     *     The message
     */
    @JsonProperty("message")
    public Message getMessage() {
        return message;
    }

    /**
     * 
     * @param message
     *     The message
     */
    @JsonProperty("message")
    public void setMessage(Message message) {
        this.message = message;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}

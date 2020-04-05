package ru.max.bot.holders;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Hold all response message with maxId for update queue
 *
 * @author Maksim Stepanov
 * @email maksim.stsiapanau@gmail.com
 */
@Data
public class Response {

    private List<ResponseHolder> responses;
    private Integer maxUpdateId;

    public List<ResponseHolder> getResponses() {
        if (null == responses) {
            this.responses = new ArrayList<>();
        }
        return this.responses;
    }


}

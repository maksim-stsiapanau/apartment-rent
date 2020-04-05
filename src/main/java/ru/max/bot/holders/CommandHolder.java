package ru.max.bot.holders;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Hold command and russian language flag
 *
 * @author Maksim Stepanov
 * @email maksim.stsiapanau@gmail.com
 */
@Data
@AllArgsConstructor
public class CommandHolder {

    private final String command;
    private final boolean rusLang;

}

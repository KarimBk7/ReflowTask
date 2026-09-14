package dev.karimbk.reflowtask.task;

import jakarta.validation.constraints.NotNull;

/**
 * Its own endpoint so marking a task done from a calendar block is one small call that
 * cannot clobber concurrently edited fields.
 */
public record TaskStatusRequest(@NotNull TaskStatus status) {
}

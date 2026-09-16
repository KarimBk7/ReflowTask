/**
 * Every user-facing string, in one place.
 *
 * PRODUCT.md commits to English kept i18n-ready: a flat dictionary behind a lookup is
 * enough for that, and it makes adding German later a data change rather than a refactor.
 * No i18n library until there is a second language to justify one.
 */
export const en = {
  'app.name': 'ReflowTask',

  'week.today': 'Today',
  'week.previous': 'Previous week',
  'week.next': 'Next week',
  'week.thisWeek': 'This week',
  'week.noWorkingHours': 'Not a working day',

  'action.replan': 'Replan now',
  'action.pin': 'Pin',
  'action.unpin': 'Unpin',
  'action.markDone': 'Mark done',
  'action.reopen': 'Reopen',
  'action.edit': 'Edit',
  'action.delete': 'Delete',
  'action.newTask': 'New task',
  'action.editTask': 'Edit task',
  'action.save': 'Save',
  'action.cancel': 'Cancel',

  'rail.title': 'Unracked',
  'rail.empty': 'Everything is scheduled.',
  'rail.unscheduled': 'No slot in the horizon',
  'rail.atRisk': 'Past its deadline',

  'task.title': 'Title',
  'task.description': 'Description',
  'task.estimate': 'Estimated duration',
  'task.deadline': 'Deadline',
  'task.deadlineTime': 'Time',
  'task.priority': 'Priority',
  'task.noDeadline': 'No deadline',

  'priority.LOW': 'Low',
  'priority.MEDIUM': 'Medium',
  'priority.HIGH': 'High',

  'status.OPEN': 'Open',
  'status.IN_PROGRESS': 'In progress',
  'status.DONE': 'Done',

  'state.atRisk': 'At risk',
  'state.movedFrom': 'Moved from',
  'state.movedFromShort': 'from',
  'state.pinned': 'Pinned',
  'state.partiallyScheduled': 'Partly scheduled',

  'history.title': 'Record',
  'history.empty': 'Nothing has moved yet.',
  'history.trigger.TASK_CHANGED': 'after an edit',
  'history.trigger.SCHEDULED_JOB': 'automatic',
  'history.trigger.MANUAL': 'by hand',
  'history.trigger.CONFIG_CHANGED': 'after the hours changed',
  'history.kind.MISSED': 'missed',
  'history.kind.MOVED': 'moved',
  'history.kind.PLACED': 'placed',
  'history.kind.UNPLACED': 'no slot',

  'hours.open': 'Hours',
  'hours.title': 'Working hours',
  'hours.working': 'Works on',
  'hours.offDay': 'Not a working day',
  'hours.blocked': 'Blocked time',
  'hours.blockedHint': 'Recurring time inside working hours that nothing is scheduled into.',
  'hours.addBlocked': 'Add blocked time',
  'hours.label': 'Label',
  'hours.from': 'From',
  'hours.until': 'Until',
  'hours.planning': 'Planning',
  'hours.horizon': 'Plan ahead',
  'hours.horizonUnit': 'days',
  'hours.minChunk': 'Smallest piece',
  'hours.minChunkUnit': 'min',
  'hours.endsBeforeStart': 'Ends before it starts.',
  'hours.invalid': 'Fix the highlighted times before saving.',
  'hours.rejected': 'The server rejected these settings. Check the hours and planning values.',
  'hours.day': 'Day',
  'action.remove': 'Remove',

  'error.generic': 'Something went wrong. Try again.',
  'error.offline': 'Cannot reach the server.',
} as const

export type StringKey = keyof typeof en

/** Lookup with the key as its own fallback, so a missing string is visible, not blank. */
export function t(key: StringKey): string {
  return en[key] ?? key
}

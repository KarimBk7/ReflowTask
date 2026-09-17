/**
 * Every user-facing string, in one place.
 *
 * PRODUCT.md commits to English kept i18n-ready: a flat dictionary behind a lookup is enough for
 * that, and it makes adding German later a data change rather than a refactor. No i18n library
 * until there is a second language to justify one.
 */
export const en = {
  'app.name': 'ReflowTask',

  'week.today': 'Today',
  'week.previous': 'Previous week',
  'week.next': 'Next week',
  'week.navigation': 'Week',

  'action.replan': 'Replan',
  'action.pin': 'Pin',
  'action.unpin': 'Unpin',
  'action.markDone': 'Mark done',
  'action.reopen': 'Reopen',
  'action.edit': 'Edit',
  'action.delete': 'Delete',
  'action.confirmDelete': 'Delete task',
  'action.newTask': 'New task',
  'action.editTask': 'Edit task',
  'action.create': 'Create',
  'action.save': 'Save',
  'action.cancel': 'Cancel',
  'action.close': 'Close',
  'action.remove': 'Remove',

  'task.title': 'Title',
  'task.titlePlaceholder': 'What needs doing?',
  'task.when': 'When',
  'task.fixedAt': 'Fixed at',
  'task.fixedHint': 'Stays at this time. Other work plans around it.',
  'task.slotPassed': 'That time has already passed.',
  'task.letPlace': 'Let ReflowTask place it',
  'task.letPlaceHint': 'Scheduled into the first free time that fits.',
  'task.estimate': 'Duration',
  'task.estimateMinutes': 'Duration in minutes',
  'task.custom': 'Custom',
  'task.priority': 'Priority',
  'task.deadline': 'Deadline',
  'task.noDeadline': 'None',
  'task.today': 'Today',
  'task.tomorrow': 'Tomorrow',
  'task.pickDate': 'Pick date',
  'task.deadlineDate': 'Deadline date',
  'task.deadlineTime': 'Deadline time (optional)',
  'task.description': 'Notes',
  'task.addDescription': 'Add notes',

  'unit.min': 'min',

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

  'details.movedFrom': 'Moved by the last replan, from',
  'details.atRisk': 'Ends after its deadline,',
  'details.pinned': 'Pinned. Replans leave it here.',
  'details.done': 'Done',
  'details.priority': 'priority',
  'details.due': 'Due',

  'grid.keysHint': 'Alt and arrow keys move this block. Alt, Shift and up or down change its length.',
  'grid.break': 'Break',
  'grid.wasHere': 'Was here',
  'grid.missedHere': 'Missed',

  'workload.of': 'of',
  'workload.free': 'free',
  'workload.planned': 'planned',
  'workload.dayOff': 'Day off',

  'attention.title': 'Needs attention',
  'attention.empty': 'Every task has its time before its deadline.',
  'attention.atRisk': 'Ends after its deadline,',
  'attention.unscheduled': 'without a time',

  'activity.title': 'Activity',
  'activity.empty': 'No replans yet. When the plan changes, every move is listed here.',
  'activity.today': 'Today',
  'activity.trigger.TASK_CHANGED': 'after an edit',
  'activity.trigger.SCHEDULED_JOB': 'automatic check',
  'activity.trigger.MANUAL': 'replanned by hand',
  'activity.trigger.CONFIG_CHANGED': 'after hours changed',
  'activity.moved': 'moved',
  'activity.reshuffled': 'had its later pieces moved',
  'activity.missed': 'missed',
  'activity.nowAt': 'now',
  'activity.placed': 'placed',
  'activity.unplaced': 'found no time',

  'hours.open': 'Hours',
  'hours.title': 'Hours and planning',
  'hours.welcomeTitle': 'Set up your week',
  'hours.welcomeText':
    'ReflowTask only places work inside your working hours. Add breaks, like lunch, that should stay free.',
  'hours.skip': 'Skip for now',
  'hours.start': 'Start planning',
  'hours.working': 'Working hours',
  'hours.offDay': 'Off',
  'hours.from': 'from',
  'hours.until': 'until',
  'hours.breaks': 'Breaks',
  'hours.breaksHint': 'Recurring time inside working hours that nothing is scheduled into.',
  'hours.breakName': 'Name, like Lunch',
  'hours.breakDays': 'Days',
  'hours.noDays': 'Pick at least one day.',
  'hours.addBreak': 'Add break',
  'hours.planning': 'Planning',
  'hours.buffer': 'Buffer between tasks',
  'hours.bufferHint': 'Kept free after each task and around fixed ones.',
  'hours.horizon': 'Plan ahead',
  'hours.horizonUnit': 'days',
  'hours.minChunk': 'Smallest piece',
  'hours.minChunkHint': 'Long tasks are never split shorter than this.',
  'hours.endsBeforeStart': 'Ends before it starts.',
  'hours.invalid': 'Fix the highlighted rows before saving.',
  'hours.rejected': 'The server rejected these settings. Check the hours and planning values.',

  'error.offline': 'Cannot reach the server.',
} as const

export type StringKey = keyof typeof en

/** Lookup with the key as its own fallback, so a missing string is visible, not blank. */
export function t(key: StringKey): string {
  return en[key] ?? key
}

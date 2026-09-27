import { de } from './de'

/**
 * Every user-facing string, in one place, with German in de.ts.
 *
 * A flat dictionary behind a lookup: two languages do not justify an i18n library. The language is
 * picked once at load (a saved choice, else the browser's), and changing it reloads the page, so
 * nothing needs to re-render on a switch.
 */
export const en = {
  'app.name': 'ReflowTask',

  'week.today': 'Today',
  'week.previous': 'Previous week',
  'week.next': 'Next week',
  'week.navigation': 'Week',

  'view.week': 'Week',
  'view.month': 'Month',
  'view.list': 'List',
  'view.switch': 'View',
  'view.daysOff': 'Days off',
  'view.daysOffHint': 'Show days without working hours too',
  'list.search': 'Search tasks and notes',
  'list.show': 'Show',
  'list.open': 'Open',
  'list.done': 'Done',
  'list.all': 'All',
  'list.empty': 'No tasks here yet.',
  'list.noMatch': 'No task matches that search.',
  'month.previous': 'Previous month',
  'month.next': 'Next month',
  'month.tasks': 'tasks',
  'month.more': 'more',

  'action.replan': 'Replan',
  'action.pin': 'Pin',
  'action.unpin': 'Unpin',
  'action.markDone': 'Mark done',
  'action.partDone': 'This part is done',
  'action.partNotDone': 'Not done after all',
  'action.didIt': 'I did this',
  'action.taskDone': 'Whole task done',
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
  'action.help': 'How planning works',

  'task.title': 'Title',
  'task.titlePlaceholder': 'What needs doing?',
  'task.when': 'When',
  'task.fixedAt': 'Fixed at',
  'task.fixedHint': 'Stays at this time. Other work plans around it.',
  'task.slotPassed': 'That time has already passed.',
  'task.letPlace': 'Let ReflowTask place it',
  'task.letPlaceHint': 'Scheduled into the first free time that fits.',
  'task.letPlaceIn': 'Goes into the first free time in your working hours',
  'task.letPlaceOrder': 'Earlier deadlines go first.',
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
  'task.notBefore': 'Not before',
  'task.notBeforeDate': 'Earliest start date',
  'task.notBeforeHint': 'Nothing of this task is planned before that day, for work that cannot start yet.',
  'task.addNotBefore': 'Set an earliest start',
  'task.anyTime': 'Any time',
  'details.notBefore': 'Not planned before',
  'task.profile': 'Planned in',
  'task.letPlaceInPersonal': 'Goes into the first free personal time',
  'profile.WORK': 'Work',
  'profile.PERSONAL': 'Personal',
  'task.after': 'After',
  'task.afterNone': 'Nothing, any time',
  'task.afterHint': 'Not planned before that task is over. If this one is more urgent, that one moves up.',
  'task.addAfter': 'Wait for another task',
  'details.after': 'After',
  'task.repeat': 'Repeats',
  'task.repeatHint': 'When this one is done, the next appears, due one step after this deadline and not planned before it. Daily means every working day.',

  'repeat.NEVER': 'Never',
  'repeat.DAILY': 'Daily',
  'repeat.WEEKLY': 'Weekly',
  'repeat.BIWEEKLY': 'Every 2 weeks',
  'repeat.MONTHLY': 'Monthly',
  'details.repeats': 'Repeats',

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
  'state.missedAt': 'Missed',
  'state.missedShort': 'missed',
  'state.pinned': 'Pinned',
  'state.partOf': 'part of',
  'state.missedPart': 'missed',

  'placed.single': 'Placed',
  'placed.split': 'Placed in',
  'placed.partsFirst': 'parts, first on',
  'placed.none': 'No free time found within your planning horizon. It is listed under Needs attention.',

  'missed.one': 'was missed and has been replanned automatically.',
  'missed.many': 'tasks were missed and have been replanned automatically.',

  'details.movedFrom': 'Moved by the last replan, from',
  'details.missedAt': 'Replanned here after it was missed at',
  'details.atRisk': 'Ends after its deadline,',
  'details.pinned': 'Pinned. Replans leave it here.',
  'details.done': 'Done',
  'details.priority.LOW': 'Low priority',
  'details.priority.MEDIUM': 'Medium priority',
  'details.priority.HIGH': 'High priority',
  'details.due': 'Due',
  'details.partOf': 'One part of a',
  'details.partTask': 'task.',
  'details.otherParts': 'Other parts this week:',
  'details.missedPart': 'Missed. This time passed unfinished, so the work was planned again. Did it after all? Mark it below.',
  'details.partDone': 'This part is done. Only the rest of the task is planned.',
  'details.of': 'of',
  'details.doneSoFar': 'done so far.',
  'details.partsElsewhere': 'The rest is in another week or not scheduled yet.',

  'help.title': 'How ReflowTask plans',
  'help.place': 'New tasks go into the first free time in your working hours',
  'help.order': 'Earlier deadlines go first, then higher priority.',
  'help.whole': 'A task stays in one block when a free gap fits it. Longer work is split into parts, none shorter than',
  'help.fixed': 'Tasks created at a fixed time, blocks you drag and pinned blocks stay where they are. Everything else is planned around them.',
  'help.missed': 'When a block\'s time passes and it is not marked done, it counts as missed and is planned again. The missed block stays on the calendar; if you did it after all, open it and choose "I did this".',
  'help.repeat': 'A repeating task shows one occurrence at a time. Finish it and the next appears, due one step later; one that was left undone stays until it is done, and missed periods are skipped rather than piled up.',
  'help.profiles': 'Mark a task Personal to plan it in your personal time (set under Hours) instead of working hours. Either way, nothing is ever planned twice at the same time.',
  'help.after': 'A task can wait for another ("Wait for another task" in the editor): it is planned only after that one is over, and an urgent task pulls what it waits for forward.',
  'help.undo': 'Moved a block or marked something done by mistake? Press Undo in the message at the bottom, or Ctrl+Z.',
  'help.parts': 'Long work is split into parts. Mark a single part done and only the rest is planned; when every part is done, so is the task.',
  'help.freeze': 'What starts soon stays put: a replan moves no block that begins within',
  'help.buffer': 'Time kept free after each task and around fixed ones:',
  'help.activity': 'Every replan is listed under Activity, and a moved block shows where it came from.',
  'help.month': 'Switch to Month for a read-only overview of the whole month. Click a day to open that week.',
  'help.list': 'List shows every task, planned or not. Search titles and notes, tick one off, or click its next time to jump to that week.',
  'help.private': 'You see only your own tasks, hours and activity. Log out with the arrow icon in the top bar.',
  'help.calendar': 'Use the calendar icon to see your plan in your phone\'s calendar app, and to add other calendars: their appointments block time, and your tasks are planned around them.',
  'calendar.title': 'Calendar sync',
  'calendar.subscribeTitle': 'Your plan in other apps',
  'calendar.subscribeHint': 'Subscribe to this link in Apple Calendar, Thunderbird or ICSx⁵ on Android. It updates as ReflowTask replans, but the app decides how often it looks (usually every 15 minutes to an hour). Changes made there do not come back.',
  'calendar.createLink': 'Create subscribe link',
  'calendar.link': 'Subscribe link',
  'calendar.copy': 'Copy link',
  'calendar.copied': 'Copied',
  'calendar.openInApp': 'Open in calendar app',
  'calendar.secretHint': 'Anyone with this link can see your plan. If it got out, make a new one: the old link stops working.',
  'calendar.renew': 'New link',
  'calendar.renewConfirm': 'Replace link? Subscriptions to the old one stop.',
  'calendar.turnOff': 'Turn off',
  'calendar.busyTitle': 'Plan around other calendars',
  'calendar.busyHint': 'Paste the .ics address of another calendar (Nextcloud, Google "secret address in iCal format", Outlook "publish calendar"). Its appointments block time; all-day and "free" events do not. Read again every hour.',
  'calendar.updated': 'read',
  'calendar.refresh': 'Read now',
  'calendar.sourceName': 'Name, e.g. Work',
  'calendar.sourceUrl': 'https://… or webcal://… address',
  'calendar.add': 'Add calendar',
  'calendar.reading': 'Reading…',
  'help.household': 'As admin, use Household accounts to add members or remove them. A new member sets their own password at first login.',

  'grid.keysHint': 'Alt and arrow keys move this block. Alt, Shift and up or down change its length. On a touch screen, press and hold a block to drag it.',
  'grid.break': 'Break',
  'grid.appointment': 'Busy',
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
  'activity.trigger.CALENDAR_SYNCED': 'after a calendar changed',
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
    'ReflowTask only places work inside your working hours. Work weekends or shifts? Switch those days on too. Add breaks, like lunch, that should stay free.',
  'hours.skip': 'Skip for now',
  'hours.start': 'Start planning',
  'hours.working': 'Working hours',
  'hours.workingHint': 'Any day can be a working day, weekends included. Work is only planned on days that are on.',
  'hours.presetWeekdays': 'Mon–Fri',
  'hours.presetSix': 'Mon–Sat',
  'hours.presetAll': 'Every day',
  'hours.offDay': 'Off',
  'hours.from': 'from',
  'hours.until': 'until',
  'hours.personal': 'Personal time',
  'hours.personalHint': 'When personal tasks may be planned, such as evenings or weekends. With none set, they go into working hours.',
  'hours.breaks': 'Breaks',
  'hours.breaksHint': 'Recurring time inside working hours that nothing is scheduled into.',
  'hours.breakName': 'Name, like Lunch',
  'hours.breakDays': 'Days',
  'hours.noDays': 'Pick at least one day.',
  'hours.addBreak': 'Add break',
  'hours.planning': 'Planning',
  'hours.buffer': 'Buffer between tasks',
  'hours.bufferHint': 'Kept free after each task and around fixed ones.',
  'hours.freeze': 'Keep upcoming work in place',
  'hours.freezeHint': 'Blocks starting within this time are not moved when something new comes in. 0 turns it off.',
  'hours.horizon': 'Plan ahead',
  'hours.horizonUnit': 'days',
  'hours.minChunk': 'Smallest piece',
  'hours.minChunkHint': 'Long tasks are never split shorter than this.',
  'hours.endsBeforeStart': 'Ends before it starts.',
  'hours.invalid': 'Fix the highlighted rows before saving.',
  'hours.rejected': 'The server rejected these settings. Check the hours and planning values.',

  'error.offline': 'Cannot reach the server.',

  'undo.action': 'Undo',
  'undo.moved': 'Block moved and pinned there.',
  'undo.done': 'Task marked done.',
  'undo.partDone': 'Part marked done.',

  'auth.loginTitle': 'Log in',
  'auth.changePasswordTitle': 'Choose a password',
  'auth.changePasswordIntro': 'This account was created with a temporary password. Choose your own before continuing.',
  'auth.username': 'Username',
  'auth.password': 'Password',
  'auth.newPassword': 'New password',
  'auth.logIn': 'Log in',
  'auth.setPassword': 'Set password',
  'auth.logout': 'Log out',
  'auth.usersTitle': 'Household accounts',
  'auth.addMember': 'Add member',
  'auth.memberUsername': 'Username for the new member',
  'auth.memberPassword': 'Temporary password',
  'auth.mustChangePassword': 'Must change password',
  'auth.you': 'you',
  'auth.admin': 'Admin',
  'auth.member': 'Member',
  'auth.forgot': 'Forgot your password? Ask the admin of this household to reset it. If you are the admin, the device owner can do it from the command line (see the setup guide).',
  'auth.account': 'Account',
  'auth.signedInAs': 'Signed in as',
  'auth.changePassword': 'Change password',
  'auth.currentPassword': 'Current password',
  'auth.passwordChanged': 'Password changed.',
  'auth.roleMember': 'Member (own tasks only)',
  'auth.roleAdmin': 'Admin (can manage accounts)',
  'auth.role': 'Role',
  'auth.resetPassword': 'Reset password',
  'auth.resetFor': 'New temporary password for',
  'auth.resetHint': 'They must choose their own at next login, and are logged out everywhere.',
  'auth.resetDone': 'Temporary password set.',
  'auth.confirmRemove': 'Remove account and all its tasks',
  'auth.removeHint': 'Removing an account deletes all of its tasks, hours and history.',
  'auth.language': 'Language',
} as const

export type StringKey = keyof typeof en

export type Language = 'en' | 'de'

const LANGUAGE_KEY = 'reflowtask-language'

function detectLanguage(): Language {
  try {
    const saved = localStorage.getItem(LANGUAGE_KEY)
    if (saved === 'en' || saved === 'de') return saved
  } catch {
    /* No storage (private window, tests): fall through to the browser's language. */
  }
  return typeof navigator !== 'undefined' && navigator.language?.toLowerCase().startsWith('de') ? 'de' : 'en'
}

export const LANGUAGE: Language = detectLanguage()

/** For dates: British English keeps "21 September", day before month, like the German order. */
export const LOCALE = LANGUAGE === 'de' ? 'de-DE' : 'en-GB'

const strings: Record<StringKey, string> = LANGUAGE === 'de' ? de : en

/** Lookup with the key as its own fallback, so a missing string is visible, not blank. */
export function t(key: StringKey): string {
  return strings[key] ?? key
}

export function setLanguage(language: Language) {
  try {
    localStorage.setItem(LANGUAGE_KEY, language)
  } catch {
    return
  }
  window.location.reload()
}

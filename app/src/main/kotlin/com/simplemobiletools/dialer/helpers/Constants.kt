package app.trusted.callerid.sms.helpers

import com.simplemobiletools.commons.helpers.TAB_CALL_HISTORY
import com.simplemobiletools.commons.helpers.TAB_CONTACTS
import com.simplemobiletools.commons.helpers.TAB_MESSAGES

// shared prefs
const val SPEED_DIAL = "speed_dial"
const val REMEMBER_SIM_PREFIX = "remember_sim_"
const val GROUP_SUBSEQUENT_CALLS = "group_subsequent_calls"
const val OPEN_DIAL_PAD_AT_LAUNCH = "open_dial_pad_at_launch"
const val DISABLE_PROXIMITY_SENSOR = "disable_proximity_sensor"
const val DISABLE_SWIPE_TO_ANSWER = "disable_swipe_to_answer"
const val SHOW_TABS = "show_tabs"
const val FAVORITES_CONTACTS_ORDER = "favorites_contacts_order"
const val FAVORITES_CUSTOM_ORDER_SELECTED = "favorites_custom_order_selected"
const val WAS_OVERLAY_SNACKBAR_CONFIRMED = "was_overlay_snackbar_confirmed"
const val DIALPAD_VIBRATION = "dialpad_vibration"
const val DIALPAD_BEEPS = "dialpad_beeps"
const val HIDE_DIALPAD_NUMBERS = "hide_dialpad_numbers"
const val ALWAYS_SHOW_FULLSCREEN = "always_show_fullscreen"
const val CUSTOM_INTENT_ACTION_UPDATE_UI = "app.videocompressor.videoconverter.update_ui"
const val PRIVACY_POLICY = "https://oxylabstudio.in/privacy-policy/"
const val TERMS_AND_CONDITION = "https://oxylabstudio.in/terms-and-conditions/"
const val MANAGE_SUBSCRIPTION = "https://play.google.com/store/account/subscriptions"
const val OXYLAB_PUB_LOGO =
    "https://play-lh.googleusercontent.com/K0Px683tkYxdrSH5KuSfPPPxvW7NALx1L0-ad-zUsdcDKHxbRg7bVOQgA8dpk8ya9Vw=s94-rw"


const val TAB_BLOCKED = 1 shl 3

const val ALL_TABS_MASK = TAB_CALL_HISTORY or TAB_CONTACTS or TAB_MESSAGES or TAB_BLOCKED
val tabsList = arrayListOf(TAB_CALL_HISTORY, TAB_CONTACTS, TAB_MESSAGES, TAB_BLOCKED)

private const val PATH = "app.trusted.callerid.sms.action."
const val ACCEPT_CALL = PATH + "accept_call"
const val DECLINE_CALL = PATH + "decline_call"

const val DIALPAD_TONE_LENGTH_MS = 150L // The length of DTMF tones in milliseconds

const val MIN_RECENTS_THRESHOLD = 30

package app.trusted.callerid.sms.interfaces

interface RefreshItemsListener {
    fun refreshItems(callback: (() -> Unit)? = null)
}

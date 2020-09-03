package xyz.hisname.fireflyiii.receiver

import android.accounts.AccountManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import xyz.hisname.fireflyiii.data.local.account.AuthenticatorManager
import xyz.hisname.fireflyiii.data.local.dao.AppDatabase
import xyz.hisname.fireflyiii.data.local.pref.AppPref
import xyz.hisname.fireflyiii.repository.models.currency.CurrencyData
import xyz.hisname.fireflyiii.repository.models.transaction.Transactions
import xyz.hisname.fireflyiii.workers.transaction.TransactionWorker
import xyz.hisname.fireflyiii.ui.notifications.NotificationUtils
import xyz.hisname.fireflyiii.util.DateTimeUtil
import java.time.OffsetDateTime
import java.util.concurrent.ThreadLocalRandom

// TODO: Remove this "automation" code and probably migrate to tasker. I really hate this piece of code
class TransactionReceiver: BroadcastReceiver()  {

    override fun onReceive(context: Context, intent: Intent) {
        if(AppPref(PreferenceManager.getDefaultSharedPreferences(context)).baseUrl.isBlank() ||
                AuthenticatorManager(AccountManager.get(context)).accessToken.isBlank()){
            val notif = NotificationUtils(context)
            notif.showNotSignedIn()
        } else {
            val transactionWorkManagerId = ThreadLocalRandom.current().nextLong()
            val destinationName = intent.getStringExtra("destination") ?: ""
            val sourceName = intent.getStringExtra("source") ?: ""
            val category = intent.getStringExtra("category")
            val transactionAmount = intent.getStringExtra("amount")
            val budget = intent.getStringExtra("budget")
            val description =  intent.getStringExtra("description")
            val tags = intent.getStringExtra("tags")
            val time = intent.getStringExtra("time")
            val date = intent.getStringExtra("date")
            val transactionData = Data.Builder()
                    .putString("description", description)
                    .putString("date", date)
                    .putString("time", time)
                    .putString("amount", transactionAmount)
                    .putString("currency", intent.getStringExtra("currency"))
                    .putString("tags", tags)
                    .putString("categoryName", category)
                    .putString("budgetName", budget)
                    .putLong("transactionWorkManagerId", transactionWorkManagerId)
                    .putString("sourceName", sourceName)
                    .putString("destinationName", destinationName)
                    .putString("piggyBank", intent.getStringExtra("piggybank"))
            val transactionDatabase = AppDatabase.getInstance(context).transactionDataDao()
            val currencyDatabase = AppDatabase.getInstance(context).currencyDataDao()
            var currency: CurrencyData
            runBlocking(Dispatchers.IO) {
                currency = currencyDatabase.getCurrencyByCode(intent.getStringExtra("currency"))[0]
            }
            val currencyAttributes = currency.currencyAttributes
            val tagsList = arrayListOf<String>()
            if(tags != null){
                tagsList.addAll(tags.split(",").map { it.trim() })
            }
            val piggyBank = intent.getStringExtra("piggyBank")
            var transactionType = ""
            when (intent.action) {
                "firefly.hisname.ADD_DEPOSIT" -> {
                    TransactionWorker.initWorker(context, transactionData, "deposit", transactionWorkManagerId)
                    transactionType = "deposit"
                }
                "firefly.hisname.ADD_WITHDRAW" -> {
                    TransactionWorker.initWorker(context, transactionData, "withdrawal", transactionWorkManagerId)
                    transactionType = "withdrawal"
                }
                "firefly.hisname.ADD_TRANSFER" -> {
                    TransactionWorker.initWorker(context, transactionData, "transfer", transactionWorkManagerId)
                    transactionType = "transfer"
                }
                else -> { }
            }
            val dateTime = if(time == null){
                DateTimeUtil.offsetDateTimeWithoutTime(date)
            } else {
                DateTimeUtil.mergeDateTimeToIso8601(date, time)
            }
            runBlocking(Dispatchers.IO){
                transactionDatabase.insert(
                        Transactions(
                                transactionWorkManagerId, transactionAmount.toDouble(),  0,
                                 budget, 0,  category, currencyAttributes?.code ?: "",
                                currencyAttributes?.decimal_places ?: 0, currency.currencyId ?: 0,
                                currencyAttributes?.name ?: "", currencyAttributes?.symbol ?: "",
                                OffsetDateTime.parse(dateTime), description, 0, destinationName,
                                "", "",  0.0, "","", 0,
                                "", "", 0, "", 0,
                                sourceName, "", tagsList, transactionType, 0, piggyBank,true)
                )
            }
        }
    }
}
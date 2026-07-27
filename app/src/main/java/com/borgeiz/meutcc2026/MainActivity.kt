package com.borgeiz.meutcc2026

import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.borgeiz.meutcc2026.data.RecurringRepository
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var labelDashboard:    TextView
    private lateinit var labelTransactions: TextView
    private lateinit var labelReports:      TextView
    private lateinit var labelProfile:      TextView

    private lateinit var iconDashboard:    ImageView
    private lateinit var iconTransactions: ImageView
    private lateinit var iconReports:      ImageView
    private lateinit var iconProfile:      ImageView

    private var currentTab = 0
    private var lastNavMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnDashboard    = findViewById<LinearLayout>(R.id.btnDashboard)
        val btnTransactions = findViewById<LinearLayout>(R.id.btnTransactions)
        val btnAdd          = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btnAdd)
        val btnReports      = findViewById<LinearLayout>(R.id.btnReports)
        val btnProfile      = findViewById<LinearLayout>(R.id.btnProfile)

        labelDashboard    = findViewById(R.id.labelDashboard)
        labelTransactions = findViewById(R.id.labelTransactions)
        labelReports      = findViewById(R.id.labelReports)
        labelProfile      = findViewById(R.id.labelProfile)

        iconDashboard    = findViewById(R.id.iconDashboard)
        iconTransactions = findViewById(R.id.iconTransactions)
        iconReports      = findViewById(R.id.iconReports)
        iconProfile      = findViewById(R.id.iconProfile)

        if (savedInstanceState == null) {
            navigateTo(DashboardFragment(), 0)
        } else {
            currentTab = savedInstanceState.getInt("current_tab", 0)
            updateBottomNav()
        }

        btnDashboard.setOnClickListener    { navigateTo(DashboardFragment(), 0) }
        btnTransactions.setOnClickListener { navigateTo(TransactionsFragment(), 1) }
        btnAdd.setOnClickListener {
            currentTab = -1
            updateBottomNav()
            supportFragmentManager.beginTransaction()
                .replace(R.id.frameContainer, AddTransactionFragment())
                .addToBackStack(null)
                .commitAllowingStateLoss()
        }
        btnReports.setOnClickListener { navigateTo(ReportsFragment(), 2) }
        btnProfile.setOnClickListener { navigateTo(ProfileFragment(), 3) }

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0 && currentTab == -1) {
                val f = supportFragmentManager.findFragmentById(R.id.frameContainer)
                currentTab = when (f) {
                    is DashboardFragment    -> 0
                    is TransactionsFragment -> 1
                    is ReportsFragment      -> 2
                    is ProfileFragment      -> 3
                    else                    -> 0
                }
                updateBottomNav()
            }
        }

        checkAndPostRecurringIfNeeded()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("current_tab", currentTab)
    }

    fun openAddTransaction() {
        currentTab = -1
        updateBottomNav()
        supportFragmentManager.beginTransaction()
            .replace(R.id.frameContainer, AddTransactionFragment())
            .addToBackStack(null)
            .commitAllowingStateLoss()
    }

    private fun navigateTo(fragment: Fragment, tab: Int) {
        val now = System.currentTimeMillis()
        if (now - lastNavMs < 200) return
        if (currentTab == tab && supportFragmentManager.findFragmentById(R.id.frameContainer) != null) return
        lastNavMs = now
        currentTab = tab
        supportFragmentManager.beginTransaction()
            .replace(R.id.frameContainer, fragment)
            .commitAllowingStateLoss()
        updateBottomNav()
    }

    private fun updateBottomNav() {
        val activeColor   = ContextCompat.getColor(this, R.color.primary)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_hint)

        listOf(labelDashboard, labelTransactions, labelReports, labelProfile).forEach {
            it.setTextColor(inactiveColor)
            it.typeface = android.graphics.Typeface.DEFAULT
        }
        listOf(iconDashboard, iconTransactions, iconReports, iconProfile).forEach {
            it.imageTintList = ColorStateList.valueOf(inactiveColor)
        }

        val activeLabel = when (currentTab) {
            0 -> labelDashboard
            1 -> labelTransactions
            2 -> labelReports
            3 -> labelProfile
            else -> null
        }
        val activeIcon = when (currentTab) {
            0 -> iconDashboard
            1 -> iconTransactions
            2 -> iconReports
            3 -> iconProfile
            else -> null
        }
        activeLabel?.setTextColor(activeColor)
        activeLabel?.typeface = android.graphics.Typeface.DEFAULT_BOLD
        activeIcon?.imageTintList = ColorStateList.valueOf(activeColor)
    }

    private fun checkAndPostRecurringIfNeeded() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val recurringRepo = RecurringRepository(uid)
        recurringRepo.loadConfig { config ->
            if (config != null) recurringRepo.checkAndPostIfNeeded(config)
        }
    }
}

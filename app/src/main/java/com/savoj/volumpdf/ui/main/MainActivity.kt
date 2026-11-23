package com.savoj.volumpdf.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.savoj.volumpdf.BuildConfig
import com.savoj.volumpdf.R
import com.savoj.volumpdf.core.ads.AdMobAdsManager
import com.savoj.volumpdf.core.ads.AdsManager
import com.savoj.volumpdf.data.model.SortOrder
import com.savoj.volumpdf.databinding.ActivityMainBinding
import com.savoj.volumpdf.ui.common.AppearancePreferences
import com.savoj.volumpdf.ui.common.LicensesDialogHelper
import com.savoj.volumpdf.ui.common.SystemEdges
import com.savoj.volumpdf.ui.common.WindowInsetsHelper
import com.savoj.volumpdf.ui.onboarding.OnboardingActivity
import com.savoj.volumpdf.ui.onboarding.OnboardingLogic
import com.savoj.volumpdf.ui.onboarding.OnboardingPreferences
import com.savoj.volumpdf.ui.reader.ReaderActivity
import com.savoj.volumpdf.ui.scanner.ScanDocumentActivity
import kotlinx.coroutines.launch
import com.savoj.volumpdf.util.ErrorMessages

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adsManager: AdsManager
    private var searchMenuItem: MenuItem? = null
    private var selectedHomePage = 0

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            handleIncomingPdfUri(uri)
        }
    }

    /** Онбординг першого запуску; після нього (або пропуску) запускається згода UMP і реклама. */
    private val onboardingLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        StartActivityForResult()
    ) {
        onboardingPending = false
        startAds()
    }

    /** true, поки онбординг на екрані: повторно його не запускаємо (зміна конфігурації, відновлення процесу). */
    private var onboardingPending = false
    private var adsStarted = false
    private var speedDialExpanded = false

    private val speedDialBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            setSpeedDialExpanded(false, animate = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adsManager = AdMobAdsManager.getInstance(this)

        setSupportActionBar(binding.toolbar)
        applyWindowInsets()

        setupViewPagerAndBottomNav()
        setupActionButtons()
        onBackPressedDispatcher.addCallback(this, speedDialBackCallback)

        onboardingPending = savedInstanceState?.getBoolean(KEY_ONBOARDING_PENDING) ?: false
        if (savedInstanceState != null) {
            setSpeedDialExpanded(savedInstanceState.getBoolean(KEY_SPEED_DIAL_EXPANDED), animate = false)
        }
        setupAdsLayoutListeners()
        // Порядок: спершу онбординг (лише при першому запуску з іконки), потім згода UMP і реклама.
        // Прапорець читається тут, в основному процесі (не в Application.onCreate).
        val documentIntent = OnboardingLogic.isDocumentIntent(intent?.action, intent?.data != null)
        if (!onboardingPending && OnboardingLogic.shouldShow(OnboardingPreferences(this).shown, documentIntent)
            && savedInstanceState == null
        ) {
            onboardingPending = true
            onboardingLauncher.launch(Intent(this, OnboardingActivity::class.java))
        } else if (!onboardingPending) {
            startAds()
        }

        handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_ONBOARDING_PENDING, onboardingPending)
        outState.putBoolean(KEY_SPEED_DIAL_EXPANDED, speedDialExpanded)
    }

    private var bottomInsetPx = 0
    private var rightInsetPx = 0

    /** Edge-to-edge: тулбар під рядком стану, вміст і FAB над нижньою навігацією та системною панеллю. */
    private fun applyWindowInsets() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val e = SystemEdges.from(insets)
            bottomInsetPx = e.bottom
            rightInsetPx = e.right
            binding.mainContent.setPadding(e.left, 0, e.right, e.bottom)
            WindowInsetsHelper.applyAppBarTopInset(binding.appBarLayout, e.top)
            updateFabClusterMargin()
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }
        androidx.core.view.ViewCompat.requestApplyInsets(binding.root)
    }

    private fun updateFabClusterMargin() {
        val params = binding.fabCluster.layoutParams as? CoordinatorLayout.LayoutParams ?: return
        val navHeight = if (binding.bottomNavigation.height > 0) {
            binding.bottomNavigation.height
        } else {
            (80 * resources.displayMetrics.density).toInt()
        }
        val bannerHeight = if (binding.bannerContainer.visibility == View.VISIBLE) {
            binding.bannerContainer.height
        } else {
            0
        }
        val basePadding = (16 * resources.displayMetrics.density).toInt()
        val targetBottom = basePadding + navHeight + bannerHeight + bottomInsetPx
        if (params.bottomMargin != targetBottom) {
            params.bottomMargin = targetBottom
        }
        // Правий відступ завжди перераховується (поворот: бічна панель навігації з'являється й зникає).
        params.marginEnd = basePadding + rightInsetPx
        params.rightMargin = basePadding + rightInsetPx
        binding.fabCluster.layoutParams = params
    }

    private fun startAds() {
        if (adsStarted) return
        adsStarted = true
        adsManager.requestConsentAndInit(this) {
            adsManager.bindAdaptiveBanner(this, binding.bannerContainer)
        }
    }

    private fun setupAdsLayoutListeners() {
        binding.bannerContainer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateFabClusterMargin()
        }
        binding.bottomNavigation.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateFabClusterMargin()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val data = intent.data
        if ((Intent.ACTION_VIEW == action || Intent.ACTION_EDIT == action) && data != null) {
            handleIncomingPdfUri(data)
        }
    }

    private fun handleIncomingPdfUri(uri: Uri) {
        viewModel.onDocumentSelected(this, uri) { doc, openedAsNew ->
            openReader(doc.uri, doc.title, openedAsNew)
        }
    }

    private fun openReader(uriString: String, title: String, openedAsNew: Boolean) {
        val readerIntent = Intent(this, ReaderActivity::class.java).apply {
            data = Uri.parse(uriString)
            putExtra(ReaderActivity.EXTRA_DOCUMENT_TITLE, title)
            putExtra(ReaderActivity.EXTRA_OPENED_AS_NEW, openedAsNew)
        }
        startActivity(readerIntent)
    }

    fun launchSafOpenDocument() {
        openDocumentLauncher.launch(arrayOf("application/pdf"))
    }

    fun createNewBlankDocumentAndOpen() {
        lifecycleScope.launch {
            try {
                val file = com.savoj.volumpdf.util.DocumentCreationHelper.createBlankDocument(this@MainActivity)
                com.savoj.volumpdf.util.DocumentCreationHelper.openNewDocument(this@MainActivity, file)
            } catch (e: Exception) {
                ErrorMessages.report("MainActivity", e)
                Toast.makeText(this@MainActivity, ErrorMessages.forFailure(e, R.string.err_create_pdf).resolve(this@MainActivity), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun resetLibraryFilters() {
        viewModel.resetLibraryFilters()
        val searchView = searchMenuItem?.actionView as? SearchView
        searchView?.setQuery("", false)
        searchMenuItem?.collapseActionView()
        invalidateOptionsMenu()
    }

    private fun setupViewPagerAndBottomNav() {
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2

            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> RecentsFragment.newInstance()
                    else -> ToolsFragment.newInstance()
                }
            }
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_library -> {
                    if (binding.viewPager.currentItem != 0) {
                        binding.viewPager.setCurrentItem(0, true)
                    }
                    true
                }
                R.id.nav_tools -> {
                    if (binding.viewPager.currentItem != 1) {
                        binding.viewPager.setCurrentItem(1, true)
                    }
                    true
                }
                else -> false
            }
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                selectedHomePage = position
                val targetNavId = if (position == 0) R.id.nav_library else R.id.nav_tools
                if (binding.bottomNavigation.selectedItemId != targetNavId) {
                    binding.bottomNavigation.selectedItemId = targetNavId
                }
                val chrome = HomeLogic.toolbarState(position)
                binding.toolbar.setTitle(if (position == 0) R.string.app_name else R.string.tab_tools)
                binding.fabCluster.visibility = if (chrome.showLibraryFab) View.VISIBLE else View.GONE
                if (!chrome.showLibraryFab) setSpeedDialExpanded(false, animate = false)
                invalidateOptionsMenu()
            }
        })
    }

    private fun setupActionButtons() {
        binding.fabOpenPdf.setOnClickListener {
            setSpeedDialExpanded(false, animate = false)
            launchSafOpenDocument()
        }
        binding.fabMore.setOnClickListener {
            setSpeedDialExpanded(!speedDialExpanded, animate = true)
        }
        binding.fabScrim.setOnClickListener {
            setSpeedDialExpanded(false, animate = true)
        }
        binding.fabCreatePdf.setOnClickListener {
            setSpeedDialExpanded(false, animate = false)
            createNewBlankDocumentAndOpen()
        }
        binding.fabScanPdf.setOnClickListener {
            setSpeedDialExpanded(false, animate = false)
            ScanDocumentActivity.start(this)
        }
        applySpeedDialOrientation()
    }

    // Низька висота (ландшафт): дії розкриття в ряд, щоб не налазити на тулбар. Activity не перестворюється при повороті.
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applySpeedDialOrientation()
    }

    private fun applySpeedDialOrientation() {
        val horizontal = HomeLogic.speedDialHorizontal(resources.configuration.screenHeightDp)
        binding.fabActions.orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        val gap = (12 * resources.displayMetrics.density).toInt()
        (binding.fabScanPdf.layoutParams as? LinearLayout.LayoutParams)?.apply {
            marginStart = if (horizontal) gap else 0
            topMargin = if (horizontal) 0 else gap
            binding.fabScanPdf.layoutParams = this
        }
    }

    /** М9: розкриття/згортання додаткових дій («Створити PDF», «Сканувати») з короткою анімацією (150 мс). */
    private fun setSpeedDialExpanded(expanded: Boolean, animate: Boolean) {
        if (expanded == speedDialExpanded && binding.fabActions.visibility == (if (expanded) View.VISIBLE else View.GONE)) return
        speedDialExpanded = expanded
        speedDialBackCallback.isEnabled = expanded
        val actions = binding.fabActions
        val scrim = binding.fabScrim
        val more = binding.fabMore
        // Значок «+» міняється на «×» (без повороту: поворот викривляє форму кнопки).
        more.setImageResource(if (expanded) R.drawable.ic_close else R.drawable.ic_add)
        more.contentDescription = getString(
            if (expanded) R.string.action_hide_more_actions else R.string.action_more_actions
        )
        val offset = 24 * resources.displayMetrics.density
        val duration = 150L
        if (expanded) {
            actions.visibility = View.VISIBLE
            scrim.visibility = View.VISIBLE
            if (animate) {
                actions.alpha = 0f
                actions.translationY = offset
                actions.animate().alpha(1f).translationY(0f).setDuration(duration).start()
                scrim.alpha = 0f
                scrim.animate().alpha(1f).setDuration(duration).start()
            } else {
                actions.alpha = 1f
                actions.translationY = 0f
                scrim.alpha = 1f
            }
        } else if (animate) {
            actions.animate().cancel()
            scrim.animate().cancel()
            actions.animate().alpha(0f).translationY(offset).setDuration(duration).withEndAction {
                if (!speedDialExpanded) actions.visibility = View.GONE
            }.start()
            scrim.animate().alpha(0f).setDuration(duration).withEndAction {
                if (!speedDialExpanded) scrim.visibility = View.GONE
            }.start()
        } else {
            actions.animate().cancel()
            scrim.animate().cancel()
            actions.visibility = View.GONE
            scrim.visibility = View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu.findItem(R.id.action_search)
        searchMenuItem = searchItem
        val searchView = searchItem?.actionView as? SearchView
        searchView?.queryHint = getString(R.string.search_hint)
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.setSearchQuery(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText.orEmpty())
                return true
            }
        })

        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        val showLibraryActions = HomeLogic.toolbarState(selectedHomePage).showLibraryActions
        menu.findItem(R.id.action_search)?.isVisible = showLibraryActions
        menu.findItem(R.id.action_filter_sort)?.isVisible = showLibraryActions
        menu.findItem(R.id.action_clear_history)?.isVisible = showLibraryActions
        val hasDocs = viewModel.hasAnyRecentDocuments.value
        menu.findItem(R.id.action_clear_history)?.isEnabled = hasDocs
        menu.findItem(R.id.action_privacy_settings)?.isVisible = adsManager.isPrivacyOptionsRequired()
        // М11: стан «Фільтр і порядок» відновлюється з ViewModel (меню перебудовується при recreate).
        menu.findItem(R.id.action_filter_favorites)?.isChecked = viewModel.favoritesOnly.value
        when (viewModel.sortOrder.value) {
            SortOrder.DATE_DESC -> menu.findItem(R.id.sort_by_date)?.isChecked = true
            SortOrder.NAME_ASC -> menu.findItem(R.id.sort_by_name)?.isChecked = true
            SortOrder.SIZE_DESC -> menu.findItem(R.id.sort_by_size)?.isChecked = true
        }
        menu.findItem(R.id.action_dynamic_colors)?.apply {
            // Material You існує лише на Android 12+; на старших пункт не показуємо.
            isVisible = com.google.android.material.color.DynamicColors.isDynamicColorAvailable()
            isChecked = AppearancePreferences(this@MainActivity).dynamicColorsEnabled
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_filter_favorites -> {
                item.isChecked = viewModel.toggleFavoritesFilter()
                true
            }
            R.id.sort_by_date -> {
                item.isChecked = true
                viewModel.setSortOrder(SortOrder.DATE_DESC)
                true
            }
            R.id.sort_by_name -> {
                item.isChecked = true
                viewModel.setSortOrder(SortOrder.NAME_ASC)
                true
            }
            R.id.sort_by_size -> {
                item.isChecked = true
                viewModel.setSortOrder(SortOrder.SIZE_DESC)
                true
            }
            R.id.action_clear_history -> {
                showClearHistoryConfirmationDialog()
                true
            }
            R.id.action_privacy_policy -> {
                openPrivacyPolicy()
                true
            }
            R.id.action_privacy_settings -> {
                adsManager.showPrivacyOptionsForm(this) {
                    adsManager.bindAdaptiveBanner(this, binding.bannerContainer)
                }
                true
            }
            R.id.action_dynamic_colors -> {
                val prefs = AppearancePreferences(this)
                prefs.dynamicColorsEnabled = !prefs.dynamicColorsEnabled
                item.isChecked = prefs.dynamicColorsEnabled
                // Нова тема застосовується при створенні Activity; recreate() зберігає стан (ViewModel, пошук, вкладка).
                recreate()
                true
            }
            R.id.action_licenses -> {
                LicensesDialogHelper.showLicensesDialog(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openPrivacyPolicy() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL)))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.privacy_policy_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val KEY_ONBOARDING_PENDING = "onboarding_pending"
        const val KEY_SPEED_DIAL_EXPANDED = "speed_dial_expanded"
    }

    fun showClearHistoryConfirmationDialog() {
        val hasFavorites = viewModel.recentDocuments.value.any { it.isFavorite }
        val dialogView = layoutInflater.inflate(R.layout.dialog_clear_history, null)
        val cbKeepFavorites = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbKeepFavorites)
        cbKeepFavorites.visibility = if (hasFavorites) View.VISIBLE else View.GONE
        cbKeepFavorites.isChecked = true

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_clear_history_title)
            .setView(dialogView)
            .setPositiveButton(R.string.action_clear_all) { _, _ ->
                val keepFav = hasFavorites && cbKeepFavorites.isChecked
                viewModel.clearHistory(keepFavorites = keepFav)
                com.savoj.volumpdf.util.PdfThumbnailLoader.clearCache()
                Toast.makeText(this, R.string.history_cleared_message, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}

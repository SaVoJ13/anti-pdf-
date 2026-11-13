package com.savoj.volumpdf.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.savoj.volumpdf.R
import com.savoj.volumpdf.core.ads.AdMobAdsManager
import com.savoj.volumpdf.core.ads.AdsManager
import com.savoj.volumpdf.data.model.SortOrder
import com.savoj.volumpdf.databinding.ActivityMainBinding
import com.savoj.volumpdf.ui.common.LicensesDialogHelper
import com.savoj.volumpdf.ui.common.SystemEdges
import com.savoj.volumpdf.ui.common.WindowInsetsHelper
import com.savoj.volumpdf.ui.reader.ReaderActivity
import com.savoj.volumpdf.ui.scanner.ScanDocumentActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adsManager: AdsManager
    private var searchMenuItem: MenuItem? = null

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            handleIncomingPdfUri(uri)
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
        setupAds()

        handleIntent(intent)
    }

    private var bottomInsetPx = 0

    /** Edge-to-edge: тулбар під рядком стану, вміст і FAB над нижньою навігацією та системною панеллю. */
    private fun applyWindowInsets() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val e = SystemEdges.from(insets)
            bottomInsetPx = e.bottom
            binding.mainContent.setPadding(e.left, 0, e.right, e.bottom)
            WindowInsetsHelper.applyAppBarTopInset(binding.appBarLayout, e.top)
            updateFabClusterMargin(e.right)
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }
        androidx.core.view.ViewCompat.requestApplyInsets(binding.root)
    }

    private fun updateFabClusterMargin(rightInset: Int = 0) {
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
        if (rightInset > 0) {
            params.rightMargin = basePadding + rightInset
        }
        binding.fabCluster.layoutParams = params
    }

    private fun setupAds() {
        adsManager.requestConsentAndInit(this) {
            adsManager.bindAdaptiveBanner(this, binding.bannerContainer)
        }

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
        viewModel.onDocumentSelected(this, uri) { doc ->
            openReader(doc.uri, doc.title)
        }
    }

    private fun openReader(uriString: String, title: String) {
        val readerIntent = Intent(this, ReaderActivity::class.java).apply {
            data = Uri.parse(uriString)
            putExtra(ReaderActivity.EXTRA_DOCUMENT_TITLE, title)
        }
        startActivity(readerIntent)
    }

    fun launchSafOpenDocument() {
        openDocumentLauncher.launch(arrayOf("application/pdf"))
    }

    fun clearSearchQuery() {
        viewModel.setSearchQuery("")
        val searchView = searchMenuItem?.actionView as? SearchView
        searchView?.setQuery("", false)
        searchMenuItem?.collapseActionView()
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
                val targetNavId = if (position == 0) R.id.nav_library else R.id.nav_tools
                if (binding.bottomNavigation.selectedItemId != targetNavId) {
                    binding.bottomNavigation.selectedItemId = targetNavId
                }
                binding.fabCluster.visibility = if (position == 0) View.VISIBLE else View.GONE
            }
        })
    }

    private fun setupActionButtons() {
        binding.fabOpenPdf.setOnClickListener {
            launchSafOpenDocument()
        }
        binding.fabScanPdf.setOnClickListener {
            ScanDocumentActivity.start(this)
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_scan -> {
                ScanDocumentActivity.start(this)
                true
            }
            R.id.action_filter_favorites -> {
                val isFiltered = viewModel.toggleFavoritesFilter()
                if (isFiltered) {
                    item.setIcon(R.drawable.ic_star_filled)
                } else {
                    item.setIcon(R.drawable.ic_star_outline)
                }
                true
            }
            R.id.sort_by_date -> {
                viewModel.setSortOrder(SortOrder.DATE_DESC)
                true
            }
            R.id.sort_by_name -> {
                viewModel.setSortOrder(SortOrder.NAME_ASC)
                true
            }
            R.id.sort_by_size -> {
                viewModel.setSortOrder(SortOrder.SIZE_DESC)
                true
            }
            R.id.action_licenses -> {
                LicensesDialogHelper.showLicensesDialog(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

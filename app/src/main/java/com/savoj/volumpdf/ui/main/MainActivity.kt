package com.savoj.volumpdf.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.savoj.volumpdf.R
import com.savoj.volumpdf.data.model.SortOrder
import com.savoj.volumpdf.databinding.ActivityMainBinding
import com.savoj.volumpdf.ui.reader.ReaderActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

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

        setSupportActionBar(binding.toolbar)

        setupViewPagerAndTabs()
        setupFab()

        handleIntent(intent)
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

    private fun setupViewPagerAndTabs() {
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2

            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> RecentsFragment.newInstance()
                    else -> ToolsFragment.newInstance()
                }
            }
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                0 -> {
                    tab.setText(R.string.tab_recents)
                    tab.setIcon(R.drawable.ic_tab_recents)
                }
                1 -> {
                    tab.setText(R.string.tab_tools)
                    tab.setIcon(R.drawable.ic_tab_tools)
                }
            }
        }.attach()
    }

    private fun setupFab() {
        binding.fabOpenPdf.setOnClickListener {
            launchSafOpenDocument()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu.findItem(R.id.action_search)
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
            else -> super.onOptionsItemSelected(item)
        }
    }
}

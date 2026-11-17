package com.savoj.volumpdf.ui.onboarding

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.savoj.volumpdf.R
import com.savoj.volumpdf.databinding.ActivityOnboardingBinding
import com.savoj.volumpdf.ui.common.SystemEdges

/**
 * Онбординг першого запуску: 3 екрани, «Пропустити» на кожному, «Далі»/«Почати».
 * Без мережі, акаунтів і аналітики; зберігає лише локальний прапорець «показано» (основний процес).
 * Системна «Назад» = «Пропустити». Поворот/зміна конфігурації зберігають поточний екран (стан ViewPager2).
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private var currentPage = 0

    private class Page(@DrawableRes val icon: Int, @StringRes val title: Int, @StringRes val body: Int)

    private val pages = listOf(
        Page(R.drawable.ic_open_pdf, R.string.onboarding_1_title, R.string.onboarding_1_body),
        Page(R.drawable.ic_edit, R.string.onboarding_2_title, R.string.onboarding_2_body),
        Page(R.drawable.ic_lock, R.string.onboarding_3_title, R.string.onboarding_3_body)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.onboardingRoot) { v, insets ->
            val e = SystemEdges.from(insets)
            v.setPadding(e.left, e.top, e.right, e.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(binding.onboardingRoot)

        binding.onboardingPager.adapter = PagesAdapter()
        buildDots()
        binding.onboardingPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                showPage(position)
            }
        })
        currentPage = OnboardingLogic.clampPage(savedInstanceState?.getInt(KEY_PAGE) ?: 0)
        binding.onboardingPager.setCurrentItem(currentPage, false)
        showPage(currentPage)

        binding.btnOnboardingSkip.setOnClickListener { finishOnboarding() }
        binding.btnOnboardingNext.setOnClickListener {
            if (OnboardingLogic.isLastPage(currentPage)) {
                finishOnboarding()
            } else {
                binding.onboardingPager.setCurrentItem(OnboardingLogic.nextPage(currentPage), true)
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishOnboarding()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PAGE, currentPage)
    }

    private fun buildDots() {
        val sizePx = (10 * resources.displayMetrics.density).toInt()
        val gapPx = (6 * resources.displayMetrics.density).toInt()
        binding.onboardingDots.removeAllViews()
        repeat(OnboardingLogic.PAGE_COUNT) {
            val dot = View(this).apply {
                setBackgroundResource(R.drawable.bg_onboarding_dot)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    marginStart = gapPx
                    marginEnd = gapPx
                }
            }
            binding.onboardingDots.addView(dot)
        }
    }

    private fun showPage(position: Int) {
        currentPage = OnboardingLogic.clampPage(position)
        for (i in 0 until binding.onboardingDots.childCount) {
            binding.onboardingDots.getChildAt(i).isSelected = i == currentPage
        }
        binding.onboardingDots.contentDescription =
            getString(R.string.onboarding_page_indicator, currentPage + 1, OnboardingLogic.PAGE_COUNT)
        binding.btnOnboardingNext.setText(
            if (OnboardingLogic.isLastPage(currentPage)) R.string.onboarding_start else R.string.onboarding_next
        )
    }

    /** Ставить локальний прапорець і повертається на головний екран. */
    private fun finishOnboarding() {
        OnboardingPreferences(this).shown = true
        setResult(Activity.RESULT_OK)
        finish()
    }

    private inner class PagesAdapter : RecyclerView.Adapter<PagesAdapter.Holder>() {
        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.ivOnboardingIcon)
            val title: TextView = view.findViewById(R.id.tvOnboardingTitle)
            val body: TextView = view.findViewById(R.id.tvOnboardingBody)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding_page, parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val page = pages[position]
            holder.icon.setImageResource(page.icon)
            holder.title.setText(page.title)
            holder.body.setText(page.body)
        }

        override fun getItemCount(): Int = pages.size
    }

    private companion object {
        const val KEY_PAGE = "onboarding_page"
    }
}

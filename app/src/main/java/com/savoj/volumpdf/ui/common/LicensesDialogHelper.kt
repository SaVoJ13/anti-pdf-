package com.savoj.volumpdf.ui.common

import android.content.Context
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.savoj.volumpdf.R

/**
 * Відображення ліцензій відкритого програмного забезпечення (OFL, Apache-2.0, MIT).
 * Забезпечує дотримання вимог ліцензій щодо поширення копій ліцензій та авторів.
 */
object LicensesDialogHelper {

    fun showLicensesDialog(context: Context) {
        val density = context.resources.displayMetrics.density
        val padH = (20 * density).toInt()
        val padV = (12 * density).toInt()

        val scrollView = ScrollView(context)
        val textView = TextView(context).apply {
            setPadding(padH, padV, padH, padV)
            text = licensesText
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextIsSelectable(true)
        }
        scrollView.addView(textView)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dialog_licenses_title)
            .setView(scrollView)
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }

    val licensesText: String = """
Volum PDF / Anti PDF is built using open-source software:

============================================================
1. androidx.pdf:pdf-viewer-fragment
============================================================
Developer: Google LLC / The Android Open Source Project
License: Apache License, Version 2.0
URL: https://developer.android.com/reference/kotlin/androidx/pdf/viewer/fragment/package-summary

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

============================================================
2. PdfBox-Android
============================================================
Developer: Tom Roush & The Apache Software Foundation
License: Apache License, Version 2.0
URL: https://github.com/TomRoush/PdfBox-Android

Portions of this software are copyright (c) 1997-2006
The Apache Software Foundation. All rights reserved.

Encryption components contain Bouncy Castle Cryptography APIs:
Copyright (c) 2000-2023 The Legion of the Bouncy Castle Inc.
Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files, to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, subject to standard MIT license terms.

============================================================
3. Roboto Font
============================================================
Copyright (c) 2011 The Roboto Project Authors
License: SIL Open Font License, Version 1.1 (OFL-1.1)
URL: https://fonts.google.com/specimen/Roboto

This Font Software is licensed under the SIL Open Font License, Version 1.1.
This license is copied below, and is also available with a FAQ at:
https://openfontlicense.org

Permission is hereby granted, free of charge, to any person obtaining
a copy of the Font Software, to use, study, copy, merge, embed, modify,
redistribute, and sell modified and unmodified copies of the Font Software,
subject to the conditions of the SIL Open Font License.

============================================================
4. AndroidX & Material Design Components
============================================================
Developer: Google LLC / The Android Open Source Project
Components: AppCompat, Material 3, ConstraintLayout, Room,
Lifecycle, Activity, Fragment, RecyclerView, ViewPager2
License: Apache License, Version 2.0
URL: https://developer.android.com/jetpack

Licensed under the Apache License, Version 2.0.

============================================================
5. Kotlin & KotlinX Coroutines
============================================================
Developer: JetBrains s.r.o.
License: Apache License, Version 2.0
URL: https://github.com/Kotlin/kotlinx.coroutines

Licensed under the Apache License, Version 2.0.
""".trimIndent()
}

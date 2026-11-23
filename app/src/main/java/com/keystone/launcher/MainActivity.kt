package com.keystone.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent

/**
 * Tek aktivite. Tum arayuz KeystoneView icinde Canvas ile cizilir.
 * Fiziksel tuslar burada yakalanip view'a iletilir.
 */
class MainActivity : Activity() {

    private lateinit var view: KeystoneView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = KeystoneView(this)
        setContentView(view)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Ses tuslarini sisteme birak, digerlerini view yonetsin.
        if (view.handleKey(keyCode)) return true
        return super.onKeyDown(keyCode, event)
    }

    // Dokunmatik geri hareketi / ekran ustu geri tusu
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        view.goBack()
    }

    // Ana ekran (Home) tusuna basilinca launcher'a donulur.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) view.goHome()
    }
}

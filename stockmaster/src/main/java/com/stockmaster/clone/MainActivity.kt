package com.aws.gestaoestoque

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.aws.gestaoestoque.ui.AwsPremiumApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AwsPremiumApp() }
    }
}

package io.github.eltonmaster.fastshopper.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModel
import io.github.eltonmaster.fastshopper.AppContainer
import io.github.eltonmaster.fastshopper.FastShopperApp

fun CreationExtras.appContainer(): AppContainer =
    (this[APPLICATION_KEY] as FastShopperApp).container

inline fun <reified VM : ViewModel> appViewModelFactory(
    crossinline create: CreationExtras.(AppContainer) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create(appContainer()) }
}

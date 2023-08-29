package com.example.pdfreader


import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class PDFViewModel: ViewModel() {

    
    private val model = PDFModel()

    private val _pagenum = MutableLiveData<Int>()

    val pagenum : LiveData<Int>
        get() {
            return _pagenum
        }

    init {
        model.pagenum.observeForever { _pagenum.value = it }
    }

    fun prevpage() {
        model.prevpage()
    }

    fun nextpage() {
        model.nextpage()
    }
}
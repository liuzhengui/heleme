package com.zhengui.waterreminder.ui.persontype

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.zhengui.waterreminder.App
import com.zhengui.waterreminder.data.entity.PersonType
import com.zhengui.waterreminder.data.repository.PersonTypeRepository
import kotlinx.coroutines.launch

class PersonTypeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PersonTypeRepository((application as App).database.personTypeDao())
    val allTypes: LiveData<List<PersonType>> = repository.getAll().asLiveData()

    fun insert(personType: PersonType, onSuccess: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insert(personType)
            onSuccess(id)
        }
    }

    fun update(personType: PersonType) {
        viewModelScope.launch { repository.update(personType) }
    }

    fun delete(personType: PersonType) {
        if (!personType.isPreset) {
            viewModelScope.launch { repository.delete(personType) }
        }
    }

    fun initPresetTypesIfNeeded() {
        viewModelScope.launch {
            val presets = repository.getPresetTypes()
            if (presets.isEmpty()) {
                repository.insert(PersonType(name = "成年男性", dailyGoalMl = 2500, defaultAmountMl = 250, reminderIntervalMin = 90, isPreset = true))
                repository.insert(PersonType(name = "成年女性", dailyGoalMl = 2000, defaultAmountMl = 200, reminderIntervalMin = 120, isPreset = true))
                repository.insert(PersonType(name = "青少年", dailyGoalMl = 1800, defaultAmountMl = 200, reminderIntervalMin = 120, isPreset = true))
                repository.insert(PersonType(name = "运动人群", dailyGoalMl = 3000, defaultAmountMl = 300, reminderIntervalMin = 60, isPreset = true))
            }
        }
    }
}

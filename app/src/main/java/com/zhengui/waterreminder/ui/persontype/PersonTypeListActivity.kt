package com.zhengui.waterreminder.ui.persontype

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.zhengui.waterreminder.databinding.ActivityPersonTypeListBinding
import com.zhengui.waterreminder.util.PreferenceManager

class PersonTypeListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonTypeListBinding
    private lateinit var viewModel: PersonTypeViewModel
    private lateinit var adapter: PersonTypeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonTypeListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[PersonTypeViewModel::class.java]

        binding.toolbar.setNavigationOnClickListener { finish() }

        val currentTypeId = PreferenceManager.getCurrentTypeId(this)

        adapter = PersonTypeAdapter(
            currentTypeId = currentTypeId,
            onSelect = { type ->
                PreferenceManager.setCurrentTypeId(this, type.id)
                setResult(Activity.RESULT_OK)
                refreshAdapter()
            },
            onEdit = { type ->
                startActivity(
                    Intent(this, PersonTypeEditActivity::class.java).apply {
                        putExtra("person_type_id", type.id)
                    }
                )
            },
            onDelete = { type ->
                viewModel.delete(type)
            }
        )

        binding.recyclerPersonTypes.layoutManager = LinearLayoutManager(this)
        binding.recyclerPersonTypes.adapter = adapter

        viewModel.allTypes.observe(this) { types ->
            adapter.submitList(types)
        }

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, PersonTypeEditActivity::class.java))
        }
    }

    private fun refreshAdapter() {
        val newCurrentTypeId = PreferenceManager.getCurrentTypeId(this)
        adapter = PersonTypeAdapter(
            currentTypeId = newCurrentTypeId,
            onSelect = { type ->
                PreferenceManager.setCurrentTypeId(this, type.id)
                setResult(Activity.RESULT_OK)
                refreshAdapter()
            },
            onEdit = { type ->
                startActivity(
                    Intent(this, PersonTypeEditActivity::class.java).apply {
                        putExtra("person_type_id", type.id)
                    }
                )
            },
            onDelete = { type ->
                viewModel.delete(type)
            }
        )
        binding.recyclerPersonTypes.adapter = adapter
        viewModel.allTypes.value?.let { adapter.submitList(it) }
    }
}

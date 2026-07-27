package com.zhengui.waterreminder.ui.persontype

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.zhengui.waterreminder.databinding.FragmentPersonTypeListBinding
import com.zhengui.waterreminder.util.PreferenceManager

class PersonTypeListFragment : Fragment() {

    private var _binding: FragmentPersonTypeListBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: PersonTypeViewModel
    private lateinit var adapter: PersonTypeAdapter

    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: Bundle?): android.view.View {
        _binding = FragmentPersonTypeListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[PersonTypeViewModel::class.java]

        val currentTypeId = PreferenceManager.getCurrentTypeId(requireContext())

        adapter = PersonTypeAdapter(
            currentTypeId = currentTypeId,
            onSelect = { type ->
                PreferenceManager.setCurrentTypeId(requireContext(), type.id)
                refreshAdapter()
            },
            onEdit = { type ->
                startActivity(
                    Intent(requireContext(), PersonTypeEditActivity::class.java).apply {
                        putExtra("person_type_id", type.id)
                    }
                )
            },
            onDelete = { type ->
                viewModel.delete(type)
            }
        )

        binding.recyclerPersonTypes.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPersonTypes.adapter = adapter

        viewModel.allTypes.observe(viewLifecycleOwner) { types ->
            adapter.submitList(types)
            binding.tvEmpty.visibility = if (types.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(requireContext(), PersonTypeEditActivity::class.java))
        }
    }

    private fun refreshAdapter() {
        val newCurrentTypeId = PreferenceManager.getCurrentTypeId(requireContext())
        adapter = PersonTypeAdapter(
            currentTypeId = newCurrentTypeId,
            onSelect = { type ->
                PreferenceManager.setCurrentTypeId(requireContext(), type.id)
                refreshAdapter()
            },
            onEdit = { type ->
                startActivity(
                    Intent(requireContext(), PersonTypeEditActivity::class.java).apply {
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

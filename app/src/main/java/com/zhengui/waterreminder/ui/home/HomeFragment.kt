package com.zhengui.waterreminder.ui.home

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.zhengui.waterreminder.R
import com.zhengui.waterreminder.data.entity.WaterRecord
import com.zhengui.waterreminder.databinding.FragmentHomeBinding
import com.zhengui.waterreminder.service.ReminderScheduler
import com.zhengui.waterreminder.util.PreferenceManager
import com.zhengui.waterreminder.ui.MainViewModel
import com.zhengui.waterreminder.util.UpdateManager
import com.zhengui.waterreminder.util.dpToPx
import com.zhengui.waterreminder.widget.WidgetUpdateHelper
import com.zhengui.waterreminder.ui.persontype.PersonTypeAdapter
import com.zhengui.waterreminder.ui.persontype.PersonTypeEditActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        viewModel.initPresetTypesIfNeeded()
        setupObservers()
        setupListeners()
        setupRecordTimeline()
        refreshGreetingAndDate()

        if (activity?.intent?.getBooleanExtra("show_quick_drink", false) == true) {
            showCustomDrinkDialog()
            activity?.intent?.removeExtra("show_quick_drink")
        }
    }

    private fun setupObservers() {
        viewModel.dailyTotal.observe(viewLifecycleOwner) { total ->
            if (_binding == null) return@observe
            val goal = viewModel.dailyGoal.value ?: 2500
            binding.progressRing.setMaxProgress(goal)
            binding.progressRing.setProgress(total)
            binding.chipRemaining.text = "剩余 ${(goal - total).coerceAtLeast(0)} ml"
        }

        viewModel.dailyGoal.observe(viewLifecycleOwner) { goal ->
            if (_binding == null) return@observe
            val total = viewModel.dailyTotal.value ?: 0
            binding.progressRing.setMaxProgress(goal)
            binding.progressRing.setProgress(total)
            binding.chipRemaining.text = "剩余 ${(goal - total).coerceAtLeast(0)} ml"
        }

        viewModel.currentType.observe(viewLifecycleOwner) { type ->
            binding.tvCurrentType.text = type?.name ?: "未选择"
        }

        viewModel.streakDays.observe(viewLifecycleOwner) { days ->
            binding.tvStreakDays.text = String.format("%02d", days)
        }

        viewModel.dailyRecords.observe(viewLifecycleOwner) { records ->
            val adapter = binding.rvTodayRecords.adapter as? TimelineAdapter
            if (records.isEmpty()) {
                binding.rvTodayRecords.visibility = View.GONE
            } else {
                binding.rvTodayRecords.visibility = View.VISIBLE
                adapter?.submitList(records)
            }
            binding.chipCount.text = "已喝 ${records.size} 次"
        }
    }

    private fun setupListeners() {
        binding.btnCheckIn.setOnClickListener { showCheckInDialog() }
        binding.layoutPersonType.setOnClickListener { showPersonTypeSelector() }

        // 进度环：点击 = 记一杯水（打开底部弹窗），长按 = 直接打卡当前类型默认量
        binding.progressRing.setOnClickListener { showCheckInDialog() }
        binding.progressRing.setOnLongClickListener {
            val amount = viewModel.currentType.value?.defaultAmountMl ?: 200
            viewModel.quickDrink()
            WidgetUpdateHelper.updateAllWidgets(requireContext())
            if (PreferenceManager.isEncouragementEnabled(requireContext())) {
                showEncouragementDialog(amount)
            }
            true
        }
    }

    private fun setupRecordTimeline() {
        binding.rvTodayRecords.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTodayRecords.adapter = TimelineAdapter()
    }

    private fun refreshGreetingAndDate() {
        val cal = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("EEEE · MMM d", Locale.getDefault())
        binding.tvDateLabel.text = dateFormat.format(cal.time)

        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val sub = when (hour) {
            in 5..11 -> "morning"
            in 12..17 -> "afternoon"
            in 18..22 -> "evening"
            else -> "night"
        }
        binding.tvGreetingMain.text = "Good"
        binding.tvGreetingSub.text = sub
    }

    private fun showCheckInDialog() {
        showDrinkBottomSheet(isQuickMode = false)
    }

    private fun showCustomDrinkDialog() {
        showDrinkBottomSheet(isQuickMode = true)
    }

    private fun showDrinkBottomSheet(isQuickMode: Boolean) {
        val bottomSheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_custom_drink, null)
        val bottomSheet = BottomSheetDialog(requireContext())
        bottomSheet.setContentView(bottomSheetView)

        val chipGroup = bottomSheetView.findViewById<ChipGroup>(R.id.chipGroupAmounts)
        val etCustomAmount = bottomSheetView.findViewById<TextInputEditText>(R.id.etCustomAmount)
        val btnConfirm = bottomSheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmDrink)

        val defaultAmount = viewModel.currentType.value?.defaultAmountMl ?: 200
        etCustomAmount.setText(defaultAmount.toString())

        val chipAmounts = mapOf(
            R.id.chip150 to 150,
            R.id.chip200 to 200,
            R.id.chip250 to 250,
            R.id.chip500 to 500
        )

        chipGroup.setOnCheckedChangeListener { _, checkedId ->
            val amount = chipAmounts[checkedId]
            if (amount != null) {
                if (isQuickMode) {
                    viewModel.customDrink(amount)
                    WidgetUpdateHelper.updateAllWidgets(requireContext())
                    bottomSheet.dismiss()
                    checkUpdateInBackground()
                } else {
                    etCustomAmount.setText(amount.toString())
                }
            }
        }

        btnConfirm.setOnClickListener {
            val amount = etCustomAmount.text.toString().toIntOrNull()
            if (amount != null && amount > 0) {
                viewModel.customDrink(amount)
                WidgetUpdateHelper.updateAllWidgets(requireContext())
                bottomSheet.dismiss()
                checkUpdateInBackground()
                if (PreferenceManager.isEncouragementEnabled(requireContext())) {
                    showEncouragementDialog(amount)
                }
            } else {
                Toast.makeText(requireContext(), "请输入有效饮水量", Toast.LENGTH_SHORT).show()
            }
        }

        bottomSheet.show()
    }

    fun showPersonTypeSelector() {
        val bottomSheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_person_type_selector, null)
        val bottomSheet = BottomSheetDialog(requireContext())
        bottomSheet.setContentView(bottomSheetView)

        val recyclerView = bottomSheetView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerTypes)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val adapter = PersonTypeAdapter(
            currentTypeId = PreferenceManager.getCurrentTypeId(requireContext()),
            onSelect = { type ->
                val oldTypeId = PreferenceManager.getCurrentTypeId(requireContext())
                PreferenceManager.setCurrentTypeId(requireContext(), type.id)
                viewModel.refreshData()
                if (oldTypeId != type.id && PreferenceManager.isReminderEnabled(requireContext())) {
                    ReminderScheduler.setLastDrinkTime(requireContext(), 0L)
                    ReminderScheduler.cancelReminder(requireContext())
                    ReminderScheduler.cancelSmallCycle(requireContext())
                    ReminderScheduler.scheduleNextReminder(requireContext(), forceReschedule = true)
                }
                bottomSheet.dismiss()
            },
            onEdit = { type ->
                bottomSheet.dismiss()
                startActivity(Intent(requireContext(), PersonTypeEditActivity::class.java).apply {
                    putExtra("person_type_id", type.id)
                })
            },
            onDelete = { type ->
                viewModel.deletePersonType(type)
                bottomSheet.dismiss()
            }
        )
        recyclerView.adapter = adapter

        viewModel.allPersonTypes.removeObservers(viewLifecycleOwner)
        viewModel.allPersonTypes.observe(viewLifecycleOwner) { types ->
            adapter.submitList(types)
            adapter.updateCurrentTypeId(PreferenceManager.getCurrentTypeId(requireContext()))
        }

        bottomSheet.show()
    }

    private val cheerGifResIds = intArrayOf(
        R.raw.cheer_gif_1,
        R.raw.cheer_gif_2,
        R.raw.cheer_gif_3,
        R.raw.cheer_gif_4,
        R.raw.cheer_gif_5
    )

    private val cheerTexts = arrayOf(
        "你真棒啊！",
        "又漂亮啦~",
        "越来越好看啦",
        "好厉害呀！",
        "你最棒啦",
        "真不错呢",
        "闪闪发光！",
        "太优秀啦",
        "元气满满哦",
        "状态满分！",
        "好样的~",
        "每天都有进步",
        "比昨天更好啦",
        "超赞的！",
        "太可爱了吧",
        "今天也在发光"
    )

    private fun checkUpdateInBackground() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val info = UpdateManager.checkForUpdate()
                if (info != null && _binding != null) {
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(info)
                    }
                }
            } catch (_: Exception) {
                // 静默失败，不打扰用户
            }
        }
    }

    private fun showUpdateDialog(info: UpdateManager.UpdateInfo) {
        val changelog = info.body.ifBlank { "无更新说明" }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("发现新版本 v${info.versionName}")
            .setMessage(changelog.take(500))
            .setPositiveButton("立即更新") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    UpdateManager.downloadAndInstall(requireContext(), info)
                }
            }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun showEncouragementDialog(@Suppress("UNUSED_PARAMETER") amount: Int) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_encouragement, null)
        val ivGif = dialogView.findViewById<ImageView>(R.id.ivCheerGif)

        val gifResId = cheerGifResIds.random()
        Glide.with(this)
            .asGif()
            .load(gifResId)
            .override(180.dpToPx(), 180.dpToPx())
            .centerInside()
            .into(ivGif)

        dialogView.findViewById<TextView>(R.id.tvEncouragementText).text = cheerTexts.random()

        ivGif.scaleX = 0f
        ivGif.scaleY = 0f
        ivGif.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500)
            .setInterpolator(OvershootInterpolator(1.5f))
            .start()

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.setCancelable(false)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        Handler(Looper.getMainLooper()).postDelayed({
            if (dialog.isShowing) dialog.dismiss()
        }, 2000)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
        refreshGreetingAndDate()
        if (PreferenceManager.isReminderEnabled(requireContext())) {
            ReminderScheduler.scheduleNextReminder(requireContext())
            ReminderScheduler.scheduleAllReminders(requireContext())
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    // --- 今日饮水时间轴适配器 ---

    inner class TimelineAdapter :
        ListAdapter<WaterRecord, TimelineAdapter.VH>(WaterRecordDiff) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_drink_timeline, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val record = getItem(position)
            val cal = Calendar.getInstance().apply { timeInMillis = record.drinkTime }
            val time = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            holder.tvTime.text = time
            holder.tvAmount.text = "${record.amountMl}ml"

            holder.itemView.setOnLongClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("删除记录")
                    .setMessage("确定删除 $time 的 ${record.amountMl}ml 喝水记录吗？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除") { _, _ ->
                        viewModel.deleteRecord(record)
                    }
                    .show()
                true
            }
        }

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTime: TextView = itemView.findViewById(R.id.tvTime)
            val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        }
    }

    companion object {
        private val WaterRecordDiff = object : DiffUtil.ItemCallback<WaterRecord>() {
            override fun areItemsTheSame(oldItem: WaterRecord, newItem: WaterRecord): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: WaterRecord, newItem: WaterRecord): Boolean =
                oldItem == newItem
        }
    }
}

//package com.example.smallbasket
//
//import android.app.AlertDialog
//import android.content.Intent
//import android.os.Bundle
//import android.view.LayoutInflater
//import android.widget.NumberPicker
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import com.example.smallbasket.databinding.ActivityOrderBinding
//import com.example.smallbasket.databinding.DialogDayHourMinuteBinding
//import java.util.*
//
//class OrderActivity : AppCompatActivity() {
//
//    private lateinit var binding: ActivityOrderBinding
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityOrderBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        setupCustomTimePickers()
//        setupListeners()
//    }
//
//    /**
//     * Opens a custom Day-Hour-Minute picker dialog for both time fields.
//     */
//    private fun setupCustomTimePickers() {
//        binding.etBestBeforeTime.setOnClickListener {
//            showDayHourMinutePicker { selected ->
//                binding.etBestBeforeTime.setText(selected)
//            }
//        }
//
//        binding.etDeadlineTime.setOnClickListener {
//            showDayHourMinutePicker { selected ->
//                binding.etDeadlineTime.setText(selected)
//            }
//        }
//        binding.btnPlaceNow.setOnClickListener {
//            val intent = Intent(this, OrderConfirmationActivity::class.java)
//            intent.putExtra("username", "Sammie") // Replace with actual username
//            intent.putExtra("order_items", "Apples, Bananas, Mangoes") // Comma separated list
//            startActivity(intent)
//            finish()
//        }
//    }
//
//    /**
//     * Creates and shows the Day-Hour-Minute Picker dialog.
//     */
//    private fun showDayHourMinutePicker(onTimeSelected: (String) -> Unit) {
//        val dialogBinding = DialogDayHourMinuteBinding.inflate(LayoutInflater.from(this))
//
//        // Setup NumberPickers
//        dialogBinding.npDay.minValue = 0
//        dialogBinding.npDay.maxValue = 30
//        dialogBinding.npDay.wrapSelectorWheel = false
//
//        dialogBinding.npHour.minValue = 0
//        dialogBinding.npHour.maxValue = 23
//        dialogBinding.npHour.wrapSelectorWheel = true
//
//        dialogBinding.npMinute.minValue = 0
//        dialogBinding.npMinute.maxValue = 59
//        dialogBinding.npMinute.wrapSelectorWheel = true
//
//        val dialog = AlertDialog.Builder(this)
//            .setTitle("Select Time (Day : Hour : Minute)")
//            .setView(dialogBinding.root)
//            .setPositiveButton("OK") { _, _ ->
//                val d = dialogBinding.npDay.value
//                val h = dialogBinding.npHour.value
//                val m = dialogBinding.npMinute.value
//                val formatted = String.format("%02d day : %02d hr : %02d min", d, h, m)
//                onTimeSelected(formatted)
//            }
//            .setNegativeButton("Cancel", null)
//            .create()
//
//        dialog.show()
//    }
//
//    /**
//     * Handles button click and data collection.
//     */
//    private fun setupListeners() {
//        binding.btnPlaceNow.setOnClickListener {
//            val itemTitle = binding.etItemTitle.text.toString().trim()
//            val pickupSpecific = binding.etSpecificPickupLocation.text.toString().trim()
//            val pickupArea = binding.spPickupLocation.selectedItem?.toString() ?: ""
//            val dropSpecific = binding.spDropLocation.text.toString().trim()
//            val dropArea = binding.spDropArea.selectedItem?.toString() ?: ""
//            val bestBeforeTime = binding.etBestBeforeTime.text.toString().trim()
//            val deadlineTime = binding.etDeadlineTime.text.toString().trim()
//            val reward = binding.tvReward.text.toString().trim()
//            val notes = binding.etNote.text.toString().trim()
//            val priority = if (binding.swPriority.isChecked) "Emergency" else "Normal"
//
//            if (itemTitle.isEmpty() || pickupArea.isEmpty() || dropArea.isEmpty()) {
//                Toast.makeText(this, "Please fill all required fields!", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
//
//            val message = """
//                🧺 Order Summary 🧺
//
//                Item: $itemTitle
//                Pickup: $pickupSpecific ($pickupArea)
//                Drop: $dropSpecific ($dropArea)
//                Reward: $reward
//                Best Before: $bestBeforeTime
//                Deadline: $deadlineTime
//                Priority: $priority
//                Notes: $notes
//            """.trimIndent()
//
//            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
//        }
//    }
//}


package com.example.smallbasket

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.smallbasket.databinding.ActivityOrderBinding
import com.example.smallbasket.databinding.DialogDayHourMinuteBinding

class OrderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDynamicItems()
        setupCustomTimePickers()
        setupListeners()
    }

    /**
     * Dynamically add new item fields.
     */
    private fun setupDynamicItems() {
        // Add first item by default
        addItemField()

        binding.btnAddItem.setOnClickListener {
            addItemField()
        }
    }

    private fun addItemField() {
        val editText = EditText(this)
        editText.hint = "Enter item title"
        editText.setPadding(24, 24, 24, 24)
        editText.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 16
        }
//        editText.background = getDrawable(R.drawable.edittext_bg) // Optional custom style

        binding.llItemContainer.addView(editText)
    }

    /**
     * Custom Day-Hour-Minute picker setup (same as before)
     */
    private fun setupCustomTimePickers() {
        binding.etBestBeforeTime.setOnClickListener {
            showDayHourMinutePicker { selected -> binding.etBestBeforeTime.setText(selected) }
        }
        binding.etDeadlineTime.setOnClickListener {
            showDayHourMinutePicker { selected -> binding.etDeadlineTime.setText(selected) }
        }
    }

    private fun showDayHourMinutePicker(onTimeSelected: (String) -> Unit) {
        val dialogBinding = DialogDayHourMinuteBinding.inflate(LayoutInflater.from(this))

        dialogBinding.npDay.minValue = 0
        dialogBinding.npDay.maxValue = 30
        dialogBinding.npDay.wrapSelectorWheel = false

        dialogBinding.npHour.minValue = 0
        dialogBinding.npHour.maxValue = 23
        dialogBinding.npHour.wrapSelectorWheel = true

        dialogBinding.npMinute.minValue = 0
        dialogBinding.npMinute.maxValue = 59
        dialogBinding.npMinute.wrapSelectorWheel = true

        AlertDialog.Builder(this)
            .setTitle("Select Time (Day : Hour : Minute)")
            .setView(dialogBinding.root)
            .setPositiveButton("OK") { _, _ ->
                val formatted =
                    String.format("%02d day : %02d hr : %02d min", dialogBinding.npDay.value,
                        dialogBinding.npHour.value,
                        dialogBinding.npMinute.value)
                onTimeSelected(formatted)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Collect all items and send to confirmation page.
     */
    private fun setupListeners() {
        binding.btnPlaceNow.setOnClickListener {

            val itemList = mutableListOf<String>()
            for (i in 0 until binding.llItemContainer.childCount) {
                val child = binding.llItemContainer.getChildAt(i)
                if (child is EditText) {
                    val text = child.text.toString().trim()
                    if (text.isNotEmpty()) itemList.add(text)
                }
            }

            if (itemList.isEmpty()) {
                Toast.makeText(this, "Please enter at least one item!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val pickupSpecific = binding.etSpecificPickupLocation.text.toString().trim()
            val pickupArea = binding.spPickupLocation.selectedItem?.toString() ?: ""
            val dropSpecific = binding.spDropLocation.text.toString().trim()
            val dropArea = binding.spDropArea.selectedItem?.toString() ?: ""
            val bestBeforeTime = binding.etBestBeforeTime.text.toString().trim()
            val deadlineTime = binding.etDeadlineTime.text.toString().trim()
            val reward = binding.tvReward.text.toString().trim()
            val notes = binding.etNote.text.toString().trim()
            val priority = if (binding.swPriority.isChecked) "Emergency" else "Normal"

            // Convert item list to comma-separated string
            val itemsString = itemList.joinToString(",")
            val username = intent.getStringExtra("username")
            val intent = Intent(this, OrderConfirmationActivity::class.java)
            intent.putExtra("username", username)
            intent.putExtra("order_items", itemsString)
            startActivity(intent)
        }
    }
}

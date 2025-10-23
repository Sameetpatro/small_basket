package com.example.smallbasket

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smallbasket.databinding.ActivityOrderBinding
import com.example.smallbasket.databinding.DialogDayHourMinuteBinding
import com.example.smallbasket.models.CreateOrderRequest
import com.example.smallbasket.repository.OrderRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class OrderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderBinding
    private val repository = OrderRepository()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDynamicItems()
        setupCustomTimePickers()
        setupListeners()
    }

    private fun setupDynamicItems() {
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
        binding.llItemContainer.addView(editText)
    }

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
                val formatted = String.format(
                    "%02d day : %02d hr : %02d min",
                    dialogBinding.npDay.value,
                    dialogBinding.npHour.value,
                    dialogBinding.npMinute.value
                )
                onTimeSelected(formatted)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

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
            val notes = binding.etNote.text.toString().trim()
            val priority = if (binding.swPriority.isChecked) "emergency" else "normal"

            if (pickupArea.isEmpty() || dropArea.isEmpty() || bestBeforeTime.isEmpty() || deadlineTime.isEmpty()) {
                Toast.makeText(this, "Please fill all required fields!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val userId = auth.currentUser?.uid ?: ""

            val orderRequest = CreateOrderRequest(
                userId = userId,
                items = itemList,
                pickupLocation = pickupSpecific,
                pickupArea = pickupArea,
                dropLocation = dropSpecific,
                dropArea = dropArea,
                rewardPercentage = 10.0,
                bestBefore = bestBeforeTime,
                deadline = deadlineTime,
                priority = priority,
                notes = notes.ifEmpty { null }
            )

            createOrder(orderRequest, itemList)
        }
    }

    private fun createOrder(request: CreateOrderRequest, itemList: List<String>) {
        binding.btnPlaceNow.isEnabled = false
        binding.btnPlaceNow.text = "Creating Order..."

        lifecycleScope.launch {
            val result = repository.createOrder(request)

            result.onSuccess { order ->
                Toast.makeText(
                    this@OrderActivity,
                    "Order created successfully!",
                    Toast.LENGTH_SHORT
                ).show()

                val username = intent.getStringExtra("username")
                val intent = Intent(this@OrderActivity, OrderConfirmationActivity::class.java)
                intent.putExtra("username", username)
                intent.putExtra("order_items", itemList.joinToString(","))
                intent.putExtra("order_id", order.id)
                startActivity(intent)
                finish()
            }

            result.onFailure { error ->
                Toast.makeText(
                    this@OrderActivity,
                    "Error: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
                binding.btnPlaceNow.isEnabled = true
                binding.btnPlaceNow.text = "PLACE NOW"
            }
        }
    }
}
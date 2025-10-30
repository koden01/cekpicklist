package com.example.cekpicklist.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.cekpicklist.R
import com.example.cekpicklist.utils.SoundSettingsManager

class BarcodeSoundSettingsFragment : DialogFragment() {
    
    private lateinit var soundSettingsManager: SoundSettingsManager
    
    // UI Components
    private lateinit var switchSoundEnabled: Switch
    private lateinit var spinnerSuccessSound: Spinner
    private lateinit var spinnerFailureSound: Spinner
    private lateinit var spinnerDoubleSound: Spinner
    private lateinit var spinnerStartSound: Spinner
    private lateinit var btnTestSuccess: Button
    private lateinit var btnTestFailure: Button
    private lateinit var btnTestDouble: Button
    private lateinit var btnTestStart: Button
    private lateinit var btnReset: Button
    private lateinit var btnSave: Button
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        dialog?.setTitle("🔊 Sound Settings")
        return inflater.inflate(R.layout.fragment_barcode_sound_settings, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize sound settings manager
        soundSettingsManager = SoundSettingsManager(requireContext())
        
        initViews(view)
        setupSpinners()
        loadCurrentSettings()
        setupClickListeners()
    }
    
    private fun initViews(view: View) {
        switchSoundEnabled = view.findViewById(R.id.switchSoundEnabled)
        spinnerSuccessSound = view.findViewById(R.id.spinnerSuccessSound)
        spinnerFailureSound = view.findViewById(R.id.spinnerFailureSound)
        spinnerDoubleSound = view.findViewById(R.id.spinnerDoubleSound)
        spinnerStartSound = view.findViewById(R.id.spinnerStartSound)
        btnTestSuccess = view.findViewById(R.id.btnTestSuccess)
        btnTestFailure = view.findViewById(R.id.btnTestFailure)
        btnTestDouble = view.findViewById(R.id.btnTestDouble)
        btnTestStart = view.findViewById(R.id.btnTestStart)
        btnReset = view.findViewById(R.id.btnReset)
        btnSave = view.findViewById(R.id.btnSave)
    }
    
    private fun setupSpinners() {
        val availableSounds = soundSettingsManager.getAvailableSounds()
        val soundAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, availableSounds)
        soundAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        
        spinnerSuccessSound.adapter = soundAdapter
        spinnerFailureSound.adapter = soundAdapter
        spinnerDoubleSound.adapter = soundAdapter
        spinnerStartSound.adapter = soundAdapter
    }
    
    private fun loadCurrentSettings() {
        // Load current settings
        switchSoundEnabled.isChecked = soundSettingsManager.isSoundEnabled()
        
        val availableSounds = soundSettingsManager.getAvailableSounds()
        
        // Set current sound selections
        val successIndex = availableSounds.indexOf(soundSettingsManager.getSuccessSound())
        if (successIndex >= 0) spinnerSuccessSound.setSelection(successIndex)
        
        val failureIndex = availableSounds.indexOf(soundSettingsManager.getFailureSound())
        if (failureIndex >= 0) spinnerFailureSound.setSelection(failureIndex)
        
        val doubleIndex = availableSounds.indexOf(soundSettingsManager.getDoubleSound())
        if (doubleIndex >= 0) spinnerDoubleSound.setSelection(doubleIndex)
        
        val startIndex = availableSounds.indexOf(soundSettingsManager.getStartSound())
        if (startIndex >= 0) spinnerStartSound.setSelection(startIndex)
    }
    
    private fun setupClickListeners() {
        btnTestSuccess.setOnClickListener {
            testSound("success", spinnerSuccessSound.selectedItem as String)
        }
        
        btnTestFailure.setOnClickListener {
            testSound("failure", spinnerFailureSound.selectedItem as String)
        }
        
        btnTestDouble.setOnClickListener {
            testSound("double", spinnerDoubleSound.selectedItem as String)
        }
        
        btnTestStart.setOnClickListener {
            testSound("start", spinnerStartSound.selectedItem as String)
        }
        
        btnReset.setOnClickListener {
            soundSettingsManager.resetToDefaults()
            loadCurrentSettings()
            Toast.makeText(requireContext(), "✅ Pengaturan sound direset ke default", Toast.LENGTH_SHORT).show()
        }
        
        btnSave.setOnClickListener {
            saveSettings()
            Toast.makeText(requireContext(), "✅ Pengaturan sound disimpan", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun testSound(soundType: String, soundFile: String) {
        // TODO: Implement sound testing
        Toast.makeText(requireContext(), "🔊 Test $soundType sound: $soundFile", Toast.LENGTH_SHORT).show()
    }
    
    private fun saveSettings() {
        // Save sound enabled/disabled
        soundSettingsManager.setSoundEnabled(switchSoundEnabled.isChecked)
        
        // Save sound selections
        soundSettingsManager.setSuccessSound(spinnerSuccessSound.selectedItem as String)
        soundSettingsManager.setFailureSound(spinnerFailureSound.selectedItem as String)
        soundSettingsManager.setDoubleSound(spinnerDoubleSound.selectedItem as String)
        soundSettingsManager.setStartSound(spinnerStartSound.selectedItem as String)
    }
}

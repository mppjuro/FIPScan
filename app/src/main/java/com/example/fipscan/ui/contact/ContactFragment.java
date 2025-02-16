package com.example.fipscan.ui.contact;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.fipscan.databinding.FragmentContactBinding;

public class ContactFragment extends Fragment {
    private FragmentContactBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentContactBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        TextView contactInfo = binding.textViewContact;
        contactInfo.setText("Miłosz Piórkowski\nEmail: mppjuro@gmail.com\nTel: 724928250");

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

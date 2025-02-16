package com.example.fipscan.ui.catslist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.fipscan.databinding.FragmentCatsListBinding;

import java.util.ArrayList;

public class CatsListFragment extends Fragment {

    private FragmentCatsListBinding binding;
    private ArrayList<String> cats;
    private ArrayAdapter<String> adapter;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCatsListBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        cats = new ArrayList<>();
        cats.add("Mruczek - A/G: 0.7");
        cats.add("Puszek - A/G: 0.5");

        ListView listView = binding.listViewCats;
        adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, cats);
        listView.setAdapter(adapter);

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

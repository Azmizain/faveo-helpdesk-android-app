package com.android.faveo.frontend.fragments.tickets;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.faveo.Constants;
import com.android.faveo.R;
import com.android.faveo.backend.api.v1.Helpdesk;
import com.android.faveo.backend.database.DatabaseHandler;
import com.android.faveo.model.TicketOverview;
import com.android.faveo.Helper;
import com.android.faveo.frontend.adapters.TicketOverviewAdapter;
import com.android.faveo.frontend.activities.TicketDetailActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ClosedTickets extends Fragment {
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    RecyclerView recyclerView;
    int currentPage = 1;
    static String nextPageURL = "";
    View rootView;
    ProgressDialog progressDialog;

    TicketOverviewAdapter ticketOverviewAdapter;
    List<TicketOverview> ticketOverviewList = new ArrayList<>();

    private boolean loading = true;
    int pastVisibleItems, visibleItemCount, totalItemCount;

    private String mParam1;
    private String mParam2;

    private OnFragmentInteractionListener mListener;

    public static ClosedTickets newInstance(String param1, String param2) {
        ClosedTickets fragment = new ClosedTickets();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    public ClosedTickets() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if (rootView == null) {
            rootView = inflater.inflate(R.layout.fragment_recycler, container, false);
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setMessage("Fetching tickets");
            progressDialog.show();
            new FetchFirst(getActivity()).execute();
        }

        return rootView;
    }

    public class FetchFirst extends AsyncTask<String, Void, String> {
        Context context;

        public FetchFirst(Context context) {
            this.context = context;
        }

        protected String doInBackground(String... urls) {
            if (nextPageURL.equals("null")) {
                return "all done";
            }
            String result = new Helpdesk().getClosedTicket();
            if (result == null)
                return null;
            String data;
            try {
                JSONObject jsonObject = new JSONObject(result);
                try {
                    data = jsonObject.getString("data");
                    nextPageURL = jsonObject.getString("next_page_url");
                } catch (JSONException e) {
                    data = jsonObject.getString("result");
                }
                JSONArray jsonArray = new JSONArray(data);
                for(int i = 0; i < jsonArray.length(); i++) {
                    TicketOverview ticketOverview = Helper.parseTicketOverview(jsonArray, i);
                    if(ticketOverview != null)
                        ticketOverviewList.add(ticketOverview);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return "success";
        }

        protected void onPostExecute(String result) {
            if (result == null) {
                Toast.makeText(getActivity(), "Something went wrong", Toast.LENGTH_LONG).show();
                return;
            }
            if (result.equals("all done")) {
                Toast.makeText(context, "All tickets loaded", Toast.LENGTH_SHORT).show();
                return;
            }
            recyclerView = (RecyclerView) rootView.findViewById(R.id.cardList);
            recyclerView.setHasFixedSize(false);
            final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
            linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
            recyclerView.setLayoutManager(linearLayoutManager);
            recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    if (dy > 0) {
                        visibleItemCount = linearLayoutManager.getChildCount();
                        totalItemCount = linearLayoutManager.getItemCount();
                        pastVisibleItems = linearLayoutManager.findFirstVisibleItemPosition();
                        if (loading) {
                            if ((visibleItemCount + pastVisibleItems) >= totalItemCount) {
                                loading = false;
                                new FetchNextPage(getActivity()).execute();
                                Toast.makeText(getActivity(), "Loading!", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
            });
            ticketOverviewAdapter = new TicketOverviewAdapter(ticketOverviewList);
            recyclerView.setAdapter(ticketOverviewAdapter);
        }
    }

    public class FetchNextPage extends AsyncTask<String, Void, String> {
        Context context;

        public FetchNextPage(Context context) {
            this.context = context;
        }

        protected String doInBackground(String... urls) {
            if (nextPageURL.equals("null")) {
                return "all done";
            }
            String result = new Helpdesk().nextPageURL(nextPageURL);
            if (result == null)
                return null;
            DatabaseHandler databaseHandler = new DatabaseHandler(context);
            databaseHandler.recreateTable();
            try {
                JSONObject jsonObject = new JSONObject(result);
                nextPageURL = jsonObject.getString("next_page_url");
                String data = jsonObject.getString("data");
                JSONArray jsonArray = new JSONArray(data);
                for(int i = 0; i < jsonArray.length(); i++) {
                    TicketOverview ticketOverview = Helper.parseTicketOverview(jsonArray, i);
                    if(ticketOverview != null) {
                        ticketOverviewList.add(ticketOverview);
                        databaseHandler.addTicketOverview(ticketOverview);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            databaseHandler.close();
            return "success";
        }

        protected void onPostExecute(String result) {
            if (result == null)
                return;
            if (result.equals("all done")) {
                Toast.makeText(context, "All tickets loaded", Toast.LENGTH_SHORT).show();
                return;
            }
            ticketOverviewAdapter.notifyDataSetChanged();
            loading = true;
        }
    }

    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnFragmentInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public interface OnFragmentInteractionListener {
        public void onFragmentInteraction(Uri uri);
    }

}
package com.etiennelawlor.moviehub.fragments;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.util.Pair;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.etiennelawlor.moviehub.R;
import com.etiennelawlor.moviehub.activities.MovieDetailsActivity;
import com.etiennelawlor.moviehub.activities.PersonDetailsActivity;
import com.etiennelawlor.moviehub.adapters.BaseAdapter;
import com.etiennelawlor.moviehub.adapters.MoviesAdapter;
import com.etiennelawlor.moviehub.adapters.PersonsAdapter;
import com.etiennelawlor.moviehub.network.MovieHubService;
import com.etiennelawlor.moviehub.network.ServiceGenerator;
import com.etiennelawlor.moviehub.network.interceptors.AuthorizedNetworkInterceptor;
import com.etiennelawlor.moviehub.network.models.Configuration;
import com.etiennelawlor.moviehub.network.models.Movie;
import com.etiennelawlor.moviehub.network.models.PeopleEnvelope;
import com.etiennelawlor.moviehub.network.models.Person;
import com.etiennelawlor.moviehub.prefs.MovieHubPrefs;
import com.etiennelawlor.moviehub.utilities.FontCache;
import com.etiennelawlor.moviehub.utilities.NetworkLogUtility;
import com.etiennelawlor.moviehub.utilities.NetworkUtility;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import jp.wasabeef.recyclerview.animators.SlideInUpAnimator;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

/**
 * Created by etiennelawlor on 12/16/16.
 */

public class PersonsFragment extends BaseFragment implements PersonsAdapter.OnItemClickListener, PersonsAdapter.OnReloadClickListener {

    // region Constants
    public static final String KEY_PERSON = "KEY_PERSON";
    private static final int PAGE_SIZE = 20;
    // endregion

    // region Views
    @BindView(R.id.rv)
    RecyclerView recyclerView;
    @BindView(R.id.error_ll)
    LinearLayout errorLinearLayout;
    @BindView(R.id.error_tv)
    TextView errorTextView;
    @BindView(R.id.pb)
    ProgressBar progressBar;
    @BindView(android.R.id.empty)
    LinearLayout emptyLinearLayout;
    // endregion

    // region Member Variables
    private PersonsAdapter personsAdapter;
    private Typeface font;
    private MovieHubService movieHubService;
    private Unbinder unbinder;
    private StaggeredGridLayoutManager layoutManager;
    private Configuration configuration;
    private CompositeSubscription compositeSubscription;
    private int currentPage = 1;
    private boolean isLastPage = false;
    private boolean isLoading = false;
    // endregion

    // region Listeners
    @OnClick(R.id.reload_btn)
    public void onReloadButtonClicked() {
        emptyLinearLayout.setVisibility(View.GONE);
        errorLinearLayout.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);

        Call getPopularPeopleCall = movieHubService.getPopularPeople(currentPage);
        calls.add(getPopularPeopleCall);
        getPopularPeopleCall.enqueue(getPopularPeopleFirstFetchCallback);
    }

    private RecyclerView.OnScrollListener recyclerViewOnScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(final RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);

            int visibleItemCount = recyclerView.getChildCount();
            int totalItemCount = recyclerView.getAdapter().getItemCount();
            int[] positions = layoutManager.findFirstVisibleItemPositions(null);
            int firstVisibleItem = positions[1];
            if (!isLoading && !isLastPage) {
                if ((visibleItemCount + firstVisibleItem) >= totalItemCount && totalItemCount > 0) {
                    loadMoreItems();
                }
            }
        }
    };

    // endregion

    // region Callbacks
    private Callback<PeopleEnvelope> getPopularPeopleFirstFetchCallback = new Callback<PeopleEnvelope>() {
        @Override
        public void onResponse(Call<PeopleEnvelope> call, Response<PeopleEnvelope> response) {
            progressBar.setVisibility(View.GONE);
            isLoading = false;

            if (!response.isSuccessful()) {
                int responseCode = response.code();
                if(responseCode == 504) { // 504 Unsatisfiable Request (only-if-cached)
//                    errorTextView.setText("Can't load data.\nCheck your network connection.");
//                    errorLinearLayout.setVisibility(View.VISIBLE);

                    return;
                }
            }

            PeopleEnvelope peopleEnvelope = response.body();

            if(peopleEnvelope != null){
                List<Person> persons = peopleEnvelope.getPersons();
                if(persons != null){
                    if(persons.size()>0)
                        personsAdapter.addAll(persons);

                    if(persons.size() >= PAGE_SIZE){
                        personsAdapter.addFooter();
                    } else {
                        isLastPage = true;
                    }
                }
            }

            if(personsAdapter.isEmpty()){
                emptyLinearLayout.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void onFailure(Call<PeopleEnvelope> call, Throwable t) {
            NetworkLogUtility.logFailure(call, t);

            if (!call.isCanceled()){
                isLoading = false;
                progressBar.setVisibility(View.GONE);

                if(NetworkUtility.isKnownException(t)){
                    errorTextView.setText("Can't load data.\nCheck your network connection.");
                    errorLinearLayout.setVisibility(View.VISIBLE);
                }
            }
        }
    };

    private Callback<PeopleEnvelope> getPopularPeopleNextFetchCallback = new Callback<PeopleEnvelope>() {
        @Override
        public void onResponse(Call<PeopleEnvelope> call, Response<PeopleEnvelope> response) {
            if (!response.isSuccessful()) {
                int responseCode = response.code();
                if(responseCode == 504) { // 504 Unsatisfiable Request (only-if-cached)
//                    errorTextView.setText("Can't load data.\nCheck your network connection.");
//                    errorLinearLayout.setVisibility(View.VISIBLE);

                    return;
                }
            }

            personsAdapter.removeFooter();
            isLoading = false;

            PeopleEnvelope peopleEnvelope = response.body();

            if(peopleEnvelope != null){
                List<Person> persons = peopleEnvelope.getPersons();
                if(persons != null){
                    if(persons.size()>0)
                        personsAdapter.addAll(persons);

                    if(persons.size() >= PAGE_SIZE){
                        personsAdapter.addFooter();
                    } else {
                        isLastPage = true;
                    }
                }
            }
        }

        @Override
        public void onFailure(Call<PeopleEnvelope> call, Throwable t) {
            NetworkLogUtility.logFailure(call, t);

            if (!call.isCanceled()){
                if(NetworkUtility.isKnownException(t)){
                    personsAdapter.updateFooter(BaseAdapter.FooterType.ERROR);
                }
            }
        }
    };
    // endregion

    // region Constructors
    public PersonsFragment() {
    }
    // endregion

    // region Factory Methods
    public static PersonsFragment newInstance() {
        return new PersonsFragment();
    }

    public static PersonsFragment newInstance(Bundle extras) {
        PersonsFragment fragment = new PersonsFragment();
        fragment.setArguments(extras);
        return fragment;
    }
    // endregion

    // region Lifecycle Methods
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        compositeSubscription = new CompositeSubscription();

        movieHubService = ServiceGenerator.createService(
                MovieHubService.class,
                MovieHubService.BASE_URL,
                new AuthorizedNetworkInterceptor(getContext()));

        font = FontCache.getTypeface("Lato-Medium.ttf", getContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_people, container, false);
        unbinder = ButterKnife.bind(this, rootView);

        return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);

        recyclerView.setLayoutManager(layoutManager);
        personsAdapter = new PersonsAdapter(getContext());
        personsAdapter.setOnItemClickListener(this);
        personsAdapter.setOnReloadClickListener(this);
        recyclerView.setItemAnimator(new SlideInUpAnimator());
        recyclerView.setAdapter(personsAdapter);

        // Pagination
        recyclerView.addOnScrollListener(recyclerViewOnScrollListener);

        configuration = MovieHubPrefs.getConfiguration(getContext());

        if(configuration != null){
            Call getPopularPeopleCall = movieHubService.getPopularPeople(currentPage);
            calls.add(getPopularPeopleCall);
            getPopularPeopleCall.enqueue(getPopularPeopleFirstFetchCallback);
        } else {
            Subscription subscription = movieHubService.getConfiguration()
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Action1<Configuration>() {
                        @Override
                        public void call(Configuration configuration) {
                            if(configuration != null){
                                MovieHubPrefs.setConfiguration(getContext(), configuration);

                                Call getPopularPeopleCall = movieHubService.getPopularPeople(currentPage);
                                calls.add(getPopularPeopleCall);
                                getPopularPeopleCall.enqueue(getPopularPeopleFirstFetchCallback);
                            }
                        }
                    }, new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            throwable.printStackTrace();
                            progressBar.setVisibility(View.GONE);
                            if (NetworkUtility.isKnownException(throwable)) {
                                errorTextView.setText("Can't load data.\nCheck your network connection.");
                                errorLinearLayout.setVisibility(View.VISIBLE);
                            }
                        }
                    });
            compositeSubscription.add(subscription);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        removeListeners();
        unbinder.unbind();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        compositeSubscription.unsubscribe();
    }

    // endregion

    // region PersonsAdapter.OnItemClickListener Methods
    @Override
    public void onItemClick(int position, View view) {
        Person person = personsAdapter.getItem(position);
        if(person != null){
            Intent intent = new Intent(getActivity(), PersonDetailsActivity.class);
            Bundle bundle = new Bundle();
            bundle.putParcelable(KEY_PERSON, person);
            intent.putExtras(bundle);

            Window window = getActivity().getWindow();
//            window.setStatusBarColor(primaryDark);

            Resources resources = view.getResources();
            Pair<View, String> personPair  = getPair(view, resources.getString(R.string.transition_person_thumbnail));

            ActivityOptionsCompat options = getActivityOptionsCompat(personPair);

            window.setExitTransition(null);
            ActivityCompat.startActivity(getActivity(), intent, options.toBundle());
        }
    }
    // endregion

    // region PersonsAdapter.OnReloadClickListener Methods
    @Override
    public void onReloadClick() {
        personsAdapter.updateFooter(BaseAdapter.FooterType.LOAD_MORE);

        Call getPopularPeopleCall = movieHubService.getPopularPeople(currentPage);
        calls.add(getPopularPeopleCall);
        getPopularPeopleCall.enqueue(getPopularPeopleNextFetchCallback);
    }
    // endregion

    // region Helper Methods
    private void removeListeners() {
        personsAdapter.setOnItemClickListener(null);
    }

    private void loadMoreItems() {
        isLoading = true;
        currentPage += 1;

        Call getPopularPeopleCall = movieHubService.getPopularPeople(currentPage);
        calls.add(getPopularPeopleCall);
        getPopularPeopleCall.enqueue(getPopularPeopleNextFetchCallback);
    }

    private ActivityOptionsCompat getActivityOptionsCompat(Pair pair){
        ActivityOptionsCompat options = null;

        Pair<View, String> bottomNavigationViewPair = getBottomNavigationViewPair();
        Pair<View, String> statusBarPair = getStatusBarPair();
        Pair<View, String> navigationBarPair  = getNavigationBarPair();

        if(pair!=null && bottomNavigationViewPair != null && statusBarPair!= null && navigationBarPair!= null){
            options = ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity(),
                    pair, bottomNavigationViewPair, statusBarPair, navigationBarPair);
        } else if(pair != null && bottomNavigationViewPair != null && statusBarPair != null){
            options = ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity(),
                    pair, bottomNavigationViewPair, statusBarPair);
        } else if(pair != null && bottomNavigationViewPair != null && navigationBarPair != null){
            options = ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity(),
                    pair, bottomNavigationViewPair, navigationBarPair);
        }

        return options;
    }

    private Pair<View, String> getPair(View view, String transition){
        Pair<View, String> posterImagePair = null;
        View posterImageView = ButterKnife.findById(view, R.id.thumbnail_iv);
        if(posterImageView != null){
            posterImagePair = Pair.create(posterImageView, transition);
        }

        return posterImagePair;
    }

    private Pair<View, String> getBottomNavigationViewPair(){
        Pair<View, String> pair = null;
        View bottomNavigationView = ButterKnife.findById(getActivity(), R.id.bottom_navigation);
        if(bottomNavigationView != null) {
            Resources resources = bottomNavigationView.getResources();
            pair = Pair.create(bottomNavigationView, resources.getString(R.string.transition_bottom_navigation));
        }
        return pair;
    }

    private Pair<View, String> getStatusBarPair(){
        Pair<View, String> pair = null;
        View statusBar = ButterKnife.findById(getActivity(), android.R.id.statusBarBackground);
        if(statusBar != null)
            pair = Pair.create(statusBar, statusBar.getTransitionName());
        return pair;
    }

    private Pair<View, String> getNavigationBarPair(){
        Pair<View, String> pair = null;
        View navigationBar = ButterKnife.findById(getActivity(), android.R.id.navigationBarBackground);
        if(navigationBar != null)
            pair = Pair.create(navigationBar, navigationBar.getTransitionName());
        return pair;
    }
    // endregion
}

package com.example.androidkotlinapp;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.view.View;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import androidx.fragment.app.Fragment;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.hilt.work.WorkerAssistedFactory;
import androidx.hilt.work.WorkerFactoryModule_ProvideFactoryFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import com.atomtasks.feature.detail.TaskDetailViewModel;
import com.atomtasks.feature.detail.TaskDetailViewModel_HiltModules;
import com.atomtasks.feature.detail.TaskDetailViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.atomtasks.feature.detail.TaskDetailViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.costular.atomtasks.agenda.ui.AgendaViewModel;
import com.costular.atomtasks.agenda.ui.AgendaViewModel_HiltModules;
import com.costular.atomtasks.agenda.ui.AgendaViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.costular.atomtasks.agenda.ui.AgendaViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.costular.atomtasks.analytics.providers.FirebaseAnalyticsProvider;
import com.costular.atomtasks.core.di.DispatcherProviderModule_ProvidesDispatcherProviderFactory;
import com.costular.atomtasks.core.locale.LocaleResolverImpl;
import com.costular.atomtasks.data.backup.BackupRepositoryImpl;
import com.costular.atomtasks.data.backup.ExportBackupUseCase;
import com.costular.atomtasks.data.backup.HasDataUseCase;
import com.costular.atomtasks.data.backup.ImportBackupUseCase;
import com.costular.atomtasks.data.backup.source.FileBackupProviderImpl;
import com.costular.atomtasks.data.database.AtomTasksDatabase;
import com.costular.atomtasks.data.database.DatabaseModule_Companion_ProvideDatabaseFactory;
import com.costular.atomtasks.data.database.RoomTransactionRunner;
import com.costular.atomtasks.data.json.JsonModule_ProvideKotlinSerializerFactory;
import com.costular.atomtasks.data.settings.GetThemeUseCase;
import com.costular.atomtasks.data.settings.IsAutoforwardTasksSettingEnabledUseCase;
import com.costular.atomtasks.data.settings.SetAutoforwardTasksInteractor;
import com.costular.atomtasks.data.settings.SetThemeUseCase;
import com.costular.atomtasks.data.settings.SettingsLocalDataSourceImpl;
import com.costular.atomtasks.data.settings.SettingsRepositoryImpl;
import com.costular.atomtasks.data.settings.dailyreminder.DailyReminderAlarmSchedulerImpl;
import com.costular.atomtasks.data.settings.dailyreminder.DailyReminderWorker;
import com.costular.atomtasks.data.settings.dailyreminder.DailyReminderWorker_AssistedFactory;
import com.costular.atomtasks.data.settings.dailyreminder.ObserveDailyReminderUseCase;
import com.costular.atomtasks.data.settings.dailyreminder.SyncDailyReminderUseCase;
import com.costular.atomtasks.data.settings.dailyreminder.UpdateDailyReminderUseCase;
import com.costular.atomtasks.data.tasks.DaosModule_ProvidesRemindersDaoFactory;
import com.costular.atomtasks.data.tasks.DaosModule_ProvidesTaskDaoFactory;
import com.costular.atomtasks.data.tasks.ReminderDao;
import com.costular.atomtasks.data.tasks.TasksDao;
import com.costular.atomtasks.data.tutorial.OnboardingShownUseCase;
import com.costular.atomtasks.data.tutorial.ShouldShowOnboardingUseCase;
import com.costular.atomtasks.data.tutorial.ShouldShowTaskOrderTutorialUseCase;
import com.costular.atomtasks.data.tutorial.TaskOrderTutorialDismissedUseCase;
import com.costular.atomtasks.data.tutorial.TutorialRepositoryImpl;
import com.costular.atomtasks.feature.onboarding.OnboardingViewModel;
import com.costular.atomtasks.feature.onboarding.OnboardingViewModel_HiltModules;
import com.costular.atomtasks.feature.onboarding.OnboardingViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.costular.atomtasks.feature.onboarding.OnboardingViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.costular.atomtasks.feature.onboarding.PrepopulateOnboardingTasksUseCase;
import com.costular.atomtasks.notifications.AndroidNotificationResources;
import com.costular.atomtasks.notifications.DailyReminderNotificationManagerImpl;
import com.costular.atomtasks.notifications.DefaultNotificationNavigationIntentFactory;
import com.costular.atomtasks.notifications.TaskNotificationManagerImpl;
import com.costular.atomtasks.postponetask.di.PostponeChoiceCalculatorModule;
import com.costular.atomtasks.postponetask.di.PostponeChoiceCalculatorModule_ProvidesPostponeChoiceCalculatorFactory;
import com.costular.atomtasks.postponetask.domain.GetPostponeChoiceListUseCase;
import com.costular.atomtasks.postponetask.ui.PostponeTaskActivity;
import com.costular.atomtasks.postponetask.ui.PostponeTaskViewModel;
import com.costular.atomtasks.postponetask.ui.PostponeTaskViewModel_HiltModules;
import com.costular.atomtasks.postponetask.ui.PostponeTaskViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.costular.atomtasks.postponetask.ui.PostponeTaskViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.costular.atomtasks.preferences.DataStoreModule;
import com.costular.atomtasks.preferences.DataStoreModule_ProvideDataStoreFactory;
import com.costular.atomtasks.review.strategy.ReviewStrategyImpl;
import com.costular.atomtasks.review.usecase.ShouldAskReviewUseCase;
import com.costular.atomtasks.settings.SettingsViewModel;
import com.costular.atomtasks.settings.SettingsViewModel_HiltModules;
import com.costular.atomtasks.settings.SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.costular.atomtasks.settings.SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.costular.atomtasks.tasks.di.ReminderManagerModule_ProvidesReminderManagerFactory;
import com.costular.atomtasks.tasks.di.SnackbarModule_ProvidesSnackbarManagerFactory;
import com.costular.atomtasks.tasks.helper.AutoforwardManager;
import com.costular.atomtasks.tasks.helper.TaskReminderManager;
import com.costular.atomtasks.tasks.helper.recurrence.RecurrenceManagerImpl;
import com.costular.atomtasks.tasks.helper.recurrence.RecurrenceScheduler;
import com.costular.atomtasks.tasks.receiver.MarkTaskAsDoneReceiver;
import com.costular.atomtasks.tasks.receiver.MarkTaskAsDoneReceiver_MembersInjector;
import com.costular.atomtasks.tasks.removal.RemoveTaskUseCase;
import com.costular.atomtasks.tasks.repository.DefaultTasksLocalDataSource;
import com.costular.atomtasks.tasks.repository.DefaultTasksRepository;
import com.costular.atomtasks.tasks.usecase.AreExactRemindersAvailable;
import com.costular.atomtasks.tasks.usecase.AutoforwardTasksUseCase;
import com.costular.atomtasks.tasks.usecase.CreateTaskUseCase;
import com.costular.atomtasks.tasks.usecase.EditTaskUseCase;
import com.costular.atomtasks.tasks.usecase.GetTaskByIdUseCase;
import com.costular.atomtasks.tasks.usecase.MoveTaskUseCase;
import com.costular.atomtasks.tasks.usecase.ObserveTasksUseCase;
import com.costular.atomtasks.tasks.usecase.PopulateRecurringTasksUseCase;
import com.costular.atomtasks.tasks.usecase.PostponeTaskUseCase;
import com.costular.atomtasks.tasks.usecase.UpdateTaskIsDoneUseCase;
import com.costular.atomtasks.tasks.usecase.UpdateTaskReminderInteractor;
import com.costular.atomtasks.tasks.worker.AutoforwardTasksWorker;
import com.costular.atomtasks.tasks.worker.AutoforwardTasksWorker_AssistedFactory;
import com.costular.atomtasks.tasks.worker.MarkTaskAsDoneWorker;
import com.costular.atomtasks.tasks.worker.MarkTaskAsDoneWorker_AssistedFactory;
import com.costular.atomtasks.tasks.worker.NotifyTaskWorker;
import com.costular.atomtasks.tasks.worker.NotifyTaskWorker_AssistedFactory;
import com.costular.atomtasks.tasks.worker.RecurrenceGenerationWorker;
import com.costular.atomtasks.tasks.worker.RecurrenceGenerationWorker_AssistedFactory;
import com.costular.atomtasks.tasks.worker.RestoreMissedTaskRemindersWorker;
import com.costular.atomtasks.tasks.worker.RestoreMissedTaskRemindersWorker_AssistedFactory;
import com.costular.atomtasks.tasks.worker.SetTasksRemindersWorker;
import com.costular.atomtasks.tasks.worker.SetTasksRemindersWorker_AssistedFactory;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.ViewModelLifecycle;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.android.internal.builders.ServiceComponentBuilder;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories_InternalFactoryFactory_Factory;
import dagger.hilt.android.internal.managers.ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory;
import dagger.hilt.android.internal.managers.SavedStateHandleHolder;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideContextFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.LazyClassKeyMap;
import dagger.internal.MapBuilder;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.SingleCheck;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;
import kotlinx.serialization.json.Json;

@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class DaggerFocusApplication_HiltComponents_SingletonC {
  private DaggerFocusApplication_HiltComponents_SingletonC() {
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ApplicationContextModule applicationContextModule;

    private DataStoreModule dataStoreModule;

    private PostponeChoiceCalculatorModule postponeChoiceCalculatorModule;

    private Builder() {
    }

    public Builder applicationContextModule(ApplicationContextModule applicationContextModule) {
      this.applicationContextModule = Preconditions.checkNotNull(applicationContextModule);
      return this;
    }

    public Builder dataStoreModule(DataStoreModule dataStoreModule) {
      this.dataStoreModule = Preconditions.checkNotNull(dataStoreModule);
      return this;
    }

    public Builder postponeChoiceCalculatorModule(
        PostponeChoiceCalculatorModule postponeChoiceCalculatorModule) {
      this.postponeChoiceCalculatorModule = Preconditions.checkNotNull(postponeChoiceCalculatorModule);
      return this;
    }

    public FocusApplication_HiltComponents.SingletonC build() {
      Preconditions.checkBuilderRequirement(applicationContextModule, ApplicationContextModule.class);
      if (dataStoreModule == null) {
        this.dataStoreModule = new DataStoreModule();
      }
      if (postponeChoiceCalculatorModule == null) {
        this.postponeChoiceCalculatorModule = new PostponeChoiceCalculatorModule();
      }
      return new SingletonCImpl(applicationContextModule, dataStoreModule, postponeChoiceCalculatorModule);
    }
  }

  private static final class ActivityRetainedCBuilder implements FocusApplication_HiltComponents.ActivityRetainedC.Builder {
    private final SingletonCImpl singletonCImpl;

    private SavedStateHandleHolder savedStateHandleHolder;

    private ActivityRetainedCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ActivityRetainedCBuilder savedStateHandleHolder(
        SavedStateHandleHolder savedStateHandleHolder) {
      this.savedStateHandleHolder = Preconditions.checkNotNull(savedStateHandleHolder);
      return this;
    }

    @Override
    public FocusApplication_HiltComponents.ActivityRetainedC build() {
      Preconditions.checkBuilderRequirement(savedStateHandleHolder, SavedStateHandleHolder.class);
      return new ActivityRetainedCImpl(singletonCImpl, savedStateHandleHolder);
    }
  }

  private static final class ActivityCBuilder implements FocusApplication_HiltComponents.ActivityC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private Activity activity;

    private ActivityCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ActivityCBuilder activity(Activity activity) {
      this.activity = Preconditions.checkNotNull(activity);
      return this;
    }

    @Override
    public FocusApplication_HiltComponents.ActivityC build() {
      Preconditions.checkBuilderRequirement(activity, Activity.class);
      return new ActivityCImpl(singletonCImpl, activityRetainedCImpl, activity);
    }
  }

  private static final class FragmentCBuilder implements FocusApplication_HiltComponents.FragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private Fragment fragment;

    private FragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public FragmentCBuilder fragment(Fragment fragment) {
      this.fragment = Preconditions.checkNotNull(fragment);
      return this;
    }

    @Override
    public FocusApplication_HiltComponents.FragmentC build() {
      Preconditions.checkBuilderRequirement(fragment, Fragment.class);
      return new FragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragment);
    }
  }

  private static final class ViewWithFragmentCBuilder implements FocusApplication_HiltComponents.ViewWithFragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private View view;

    private ViewWithFragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;
    }

    @Override
    public ViewWithFragmentCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public FocusApplication_HiltComponents.ViewWithFragmentC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewWithFragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl, view);
    }
  }

  private static final class ViewCBuilder implements FocusApplication_HiltComponents.ViewC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private View view;

    private ViewCBuilder(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public ViewCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public FocusApplication_HiltComponents.ViewC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, view);
    }
  }

  private static final class ViewModelCBuilder implements FocusApplication_HiltComponents.ViewModelC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private SavedStateHandle savedStateHandle;

    private ViewModelLifecycle viewModelLifecycle;

    private ViewModelCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ViewModelCBuilder savedStateHandle(SavedStateHandle handle) {
      this.savedStateHandle = Preconditions.checkNotNull(handle);
      return this;
    }

    @Override
    public ViewModelCBuilder viewModelLifecycle(ViewModelLifecycle viewModelLifecycle) {
      this.viewModelLifecycle = Preconditions.checkNotNull(viewModelLifecycle);
      return this;
    }

    @Override
    public FocusApplication_HiltComponents.ViewModelC build() {
      Preconditions.checkBuilderRequirement(savedStateHandle, SavedStateHandle.class);
      Preconditions.checkBuilderRequirement(viewModelLifecycle, ViewModelLifecycle.class);
      return new ViewModelCImpl(singletonCImpl, activityRetainedCImpl, savedStateHandle, viewModelLifecycle);
    }
  }

  private static final class ServiceCBuilder implements FocusApplication_HiltComponents.ServiceC.Builder {
    private final SingletonCImpl singletonCImpl;

    private Service service;

    private ServiceCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ServiceCBuilder service(Service service) {
      this.service = Preconditions.checkNotNull(service);
      return this;
    }

    @Override
    public FocusApplication_HiltComponents.ServiceC build() {
      Preconditions.checkBuilderRequirement(service, Service.class);
      return new ServiceCImpl(singletonCImpl, service);
    }
  }

  private static final class ViewWithFragmentCImpl extends FocusApplication_HiltComponents.ViewWithFragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private final ViewWithFragmentCImpl viewWithFragmentCImpl = this;

    ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;


    }
  }

  private static final class FragmentCImpl extends FocusApplication_HiltComponents.FragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl = this;

    FragmentCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, Fragment fragmentParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return activityCImpl.getHiltInternalFactoryFactory();
    }

    @Override
    public ViewWithFragmentComponentBuilder viewWithFragmentComponentBuilder() {
      return new ViewWithFragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl);
    }
  }

  private static final class ViewCImpl extends FocusApplication_HiltComponents.ViewC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final ViewCImpl viewCImpl = this;

    ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }
  }

  private static final class ActivityCImpl extends FocusApplication_HiltComponents.ActivityC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl = this;

    ActivityCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    Map keySetMapOfClassOfAndBooleanBuilder() {
      MapBuilder mapBuilder = MapBuilder.<String, Boolean>newMapBuilder(5);
      mapBuilder.put(AgendaViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, AgendaViewModel_HiltModules.KeyModule.provide());
      mapBuilder.put(OnboardingViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, OnboardingViewModel_HiltModules.KeyModule.provide());
      mapBuilder.put(PostponeTaskViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, PostponeTaskViewModel_HiltModules.KeyModule.provide());
      mapBuilder.put(SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, SettingsViewModel_HiltModules.KeyModule.provide());
      mapBuilder.put(TaskDetailViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, TaskDetailViewModel_HiltModules.KeyModule.provide());
      return mapBuilder.build();
    }

    @Override
    public void injectPostponeTaskActivity(PostponeTaskActivity arg0) {
    }

    @Override
    public void injectMainActivity(MainActivity arg0) {
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Map<Class<?>, Boolean> getViewModelKeys() {
      return LazyClassKeyMap.<Boolean>of(keySetMapOfClassOfAndBooleanBuilder());
    }

    @Override
    public ViewModelComponentBuilder getViewModelComponentBuilder() {
      return new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public FragmentComponentBuilder fragmentComponentBuilder() {
      return new FragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @Override
    public ViewComponentBuilder viewComponentBuilder() {
      return new ViewCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }
  }

  private static final class ViewModelCImpl extends FocusApplication_HiltComponents.ViewModelC {
    private final SavedStateHandle savedStateHandle;

    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    Provider<AgendaViewModel> agendaViewModelProvider;

    Provider<OnboardingViewModel> onboardingViewModelProvider;

    Provider<PostponeTaskViewModel> postponeTaskViewModelProvider;

    Provider<SettingsViewModel> settingsViewModelProvider;

    Provider<TaskDetailViewModel> taskDetailViewModelProvider;

    ViewModelCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        SavedStateHandle savedStateHandleParam, ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.savedStateHandle = savedStateHandleParam;
      initialize(savedStateHandleParam, viewModelLifecycleParam);

    }

    RemoveTaskUseCase removeTaskUseCase() {
      return new RemoveTaskUseCase(singletonCImpl.defaultTasksRepository(), singletonCImpl.taskReminderManager(), singletonCImpl.taskNotificationManagerImpl());
    }

    AutoforwardManager autoforwardManager() {
      return new AutoforwardManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.isAutoforwardTasksSettingEnabledUseCase());
    }

    MoveTaskUseCase moveTaskUseCase() {
      return new MoveTaskUseCase(singletonCImpl.defaultTasksRepository());
    }

    ShouldShowTaskOrderTutorialUseCase shouldShowTaskOrderTutorialUseCase() {
      return new ShouldShowTaskOrderTutorialUseCase(singletonCImpl.tutorialRepositoryImpl());
    }

    TaskOrderTutorialDismissedUseCase taskOrderTutorialDismissedUseCase() {
      return new TaskOrderTutorialDismissedUseCase(singletonCImpl.tutorialRepositoryImpl());
    }

    ShouldAskReviewUseCase shouldAskReviewUseCase() {
      return new ShouldAskReviewUseCase(singletonCImpl.reviewStrategyImpl());
    }

    ShouldShowOnboardingUseCase shouldShowOnboardingUseCase() {
      return new ShouldShowOnboardingUseCase(singletonCImpl.tutorialRepositoryImpl());
    }

    CreateTaskUseCase createTaskUseCase() {
      return new CreateTaskUseCase(singletonCImpl.defaultTasksRepository(), singletonCImpl.taskReminderManager(), singletonCImpl.populateRecurringTasksUseCase(), singletonCImpl.settingsRepositoryImpl());
    }

    PrepopulateOnboardingTasksUseCase prepopulateOnboardingTasksUseCase() {
      return new PrepopulateOnboardingTasksUseCase(singletonCImpl.localeResolverImpl(), singletonCImpl.defaultTasksRepository(), createTaskUseCase());
    }

    OnboardingShownUseCase onboardingShownUseCase() {
      return new OnboardingShownUseCase(singletonCImpl.tutorialRepositoryImpl());
    }

    GetPostponeChoiceListUseCase getPostponeChoiceListUseCase() {
      return new GetPostponeChoiceListUseCase(PostponeChoiceCalculatorModule_ProvidesPostponeChoiceCalculatorFactory.providesPostponeChoiceCalculator(singletonCImpl.postponeChoiceCalculatorModule));
    }

    UpdateTaskReminderInteractor updateTaskReminderInteractor() {
      return new UpdateTaskReminderInteractor(singletonCImpl.defaultTasksRepository());
    }

    PostponeTaskUseCase postponeTaskUseCase() {
      return new PostponeTaskUseCase(singletonCImpl.getTaskByIdUseCase(), updateTaskReminderInteractor(), singletonCImpl.taskReminderManager(), singletonCImpl.editTaskUseCaseProvider.get());
    }

    GetThemeUseCase getThemeUseCase() {
      return new GetThemeUseCase(singletonCImpl.settingsRepositoryImpl());
    }

    SetThemeUseCase setThemeUseCase() {
      return new SetThemeUseCase(singletonCImpl.settingsRepositoryImpl());
    }

    SetAutoforwardTasksInteractor setAutoforwardTasksInteractor() {
      return new SetAutoforwardTasksInteractor(singletonCImpl.settingsRepositoryImpl());
    }

    UpdateDailyReminderUseCase updateDailyReminderUseCase() {
      return new UpdateDailyReminderUseCase(singletonCImpl.settingsRepositoryImpl(), singletonCImpl.syncDailyReminderUseCase());
    }

    AreExactRemindersAvailable areExactRemindersAvailable() {
      return new AreExactRemindersAvailable(singletonCImpl.taskReminderManager());
    }

    ExportBackupUseCase exportBackupUseCase() {
      return new ExportBackupUseCase(singletonCImpl.backupRepositoryImpl());
    }

    ImportBackupUseCase importBackupUseCase() {
      return new ImportBackupUseCase(singletonCImpl.backupRepositoryImpl());
    }

    HasDataUseCase hasDataUseCase() {
      return new HasDataUseCase(singletonCImpl.settingsRepositoryImpl());
    }

    Map hiltViewModelMapMapOfClassOfAndProviderOfViewModelBuilder() {
      MapBuilder mapBuilder = MapBuilder.<String, javax.inject.Provider<ViewModel>>newMapBuilder(5);
      mapBuilder.put(AgendaViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (agendaViewModelProvider)));
      mapBuilder.put(OnboardingViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (onboardingViewModelProvider)));
      mapBuilder.put(PostponeTaskViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (postponeTaskViewModelProvider)));
      mapBuilder.put(SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (settingsViewModelProvider)));
      mapBuilder.put(TaskDetailViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (taskDetailViewModelProvider)));
      return mapBuilder.build();
    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.agendaViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
      this.onboardingViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 1);
      this.postponeTaskViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 2);
      this.settingsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 3);
      this.taskDetailViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 4);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(hiltViewModelMapMapOfClassOfAndProviderOfViewModelBuilder());
    }

    @Override
    public Map<Class<?>, Object> getHiltViewModelAssistedMap() {
      return Collections.<Class<?>, Object>emptyMap();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final ViewModelCImpl viewModelCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          ViewModelCImpl viewModelCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.viewModelCImpl = viewModelCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // com.costular.atomtasks.agenda.ui.AgendaViewModel
          return (T) new AgendaViewModel(singletonCImpl.observeTasksUseCase(), singletonCImpl.updateTaskIsDoneUseCase(), viewModelCImpl.removeTaskUseCase(), viewModelCImpl.autoforwardManager(), viewModelCImpl.moveTaskUseCase(), new FirebaseAnalyticsProvider(), viewModelCImpl.shouldShowTaskOrderTutorialUseCase(), viewModelCImpl.taskOrderTutorialDismissedUseCase(), viewModelCImpl.shouldAskReviewUseCase(), singletonCImpl.recurrenceScheduler(), viewModelCImpl.shouldShowOnboardingUseCase(), SnackbarModule_ProvidesSnackbarManagerFactory.providesSnackbarManager(), ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 1: // com.costular.atomtasks.feature.onboarding.OnboardingViewModel
          return (T) new OnboardingViewModel(new FirebaseAnalyticsProvider(), viewModelCImpl.prepopulateOnboardingTasksUseCase(), viewModelCImpl.onboardingShownUseCase());

          case 2: // com.costular.atomtasks.postponetask.ui.PostponeTaskViewModel
          return (T) new PostponeTaskViewModel(viewModelCImpl.getPostponeChoiceListUseCase(), viewModelCImpl.postponeTaskUseCase(), singletonCImpl.taskNotificationManagerImpl(), new FirebaseAnalyticsProvider());

          case 3: // com.costular.atomtasks.settings.SettingsViewModel
          return (T) new SettingsViewModel(viewModelCImpl.getThemeUseCase(), viewModelCImpl.setThemeUseCase(), singletonCImpl.isAutoforwardTasksSettingEnabledUseCase(), viewModelCImpl.setAutoforwardTasksInteractor(), singletonCImpl.observeDailyReminderUseCase(), viewModelCImpl.updateDailyReminderUseCase(), new FirebaseAnalyticsProvider(), viewModelCImpl.areExactRemindersAvailable(), viewModelCImpl.exportBackupUseCase(), viewModelCImpl.importBackupUseCase(), viewModelCImpl.hasDataUseCase(), SnackbarModule_ProvidesSnackbarManagerFactory.providesSnackbarManager(), ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 4: // com.atomtasks.feature.detail.TaskDetailViewModel
          return (T) new TaskDetailViewModel(viewModelCImpl.savedStateHandle, viewModelCImpl.areExactRemindersAvailable(), singletonCImpl.getTaskByIdUseCase(), singletonCImpl.editTaskUseCaseProvider.get(), viewModelCImpl.createTaskUseCase(), viewModelCImpl.removeTaskUseCase(), new FirebaseAnalyticsProvider(), singletonCImpl.updateTaskIsDoneUseCase(), SnackbarModule_ProvidesSnackbarManagerFactory.providesSnackbarManager(), ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends FocusApplication_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
        SavedStateHandleHolder savedStateHandleHolderParam) {
      this.singletonCImpl = singletonCImpl;

      initialize(savedStateHandleHolderParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandleHolder savedStateHandleHolderParam) {
      this.provideActivityRetainedLifecycleProvider = DoubleCheck.provider(new SwitchingProvider<ActivityRetainedLifecycle>(singletonCImpl, activityRetainedCImpl, 0));
    }

    @Override
    public ActivityComponentBuilder activityComponentBuilder() {
      return new ActivityCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public ActivityRetainedLifecycle getActivityRetainedLifecycle() {
      return provideActivityRetainedLifecycleProvider.get();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // dagger.hilt.android.ActivityRetainedLifecycle
          return (T) ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory.provideActivityRetainedLifecycle();

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ServiceCImpl extends FocusApplication_HiltComponents.ServiceC {
    private final SingletonCImpl singletonCImpl;

    private final ServiceCImpl serviceCImpl = this;

    ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
      this.singletonCImpl = singletonCImpl;


    }
  }

  private static final class SingletonCImpl extends FocusApplication_HiltComponents.SingletonC {
    private final DataStoreModule dataStoreModule;

    private final ApplicationContextModule applicationContextModule;

    private final PostponeChoiceCalculatorModule postponeChoiceCalculatorModule;

    private final SingletonCImpl singletonCImpl = this;

    Provider<DataStore<Preferences>> provideDataStoreProvider;

    Provider<Json> provideKotlinSerializerProvider;

    Provider<AtomTasksDatabase> provideDatabaseProvider;

    Provider<EditTaskUseCase> editTaskUseCaseProvider;

    Provider<AutoforwardTasksWorker_AssistedFactory> autoforwardTasksWorker_AssistedFactoryProvider;

    Provider<DailyReminderWorker_AssistedFactory> dailyReminderWorker_AssistedFactoryProvider;

    Provider<MarkTaskAsDoneWorker_AssistedFactory> markTaskAsDoneWorker_AssistedFactoryProvider;

    Provider<NotifyTaskWorker_AssistedFactory> notifyTaskWorker_AssistedFactoryProvider;

    Provider<RecurrenceGenerationWorker_AssistedFactory> recurrenceGenerationWorker_AssistedFactoryProvider;

    Provider<RestoreMissedTaskRemindersWorker_AssistedFactory> restoreMissedTaskRemindersWorker_AssistedFactoryProvider;

    Provider<SetTasksRemindersWorker_AssistedFactory> setTasksRemindersWorker_AssistedFactoryProvider;

    SingletonCImpl(ApplicationContextModule applicationContextModuleParam,
        DataStoreModule dataStoreModuleParam,
        PostponeChoiceCalculatorModule postponeChoiceCalculatorModuleParam) {
      this.dataStoreModule = dataStoreModuleParam;
      this.applicationContextModule = applicationContextModuleParam;
      this.postponeChoiceCalculatorModule = postponeChoiceCalculatorModuleParam;
      initialize(applicationContextModuleParam, dataStoreModuleParam, postponeChoiceCalculatorModuleParam);

    }

    SettingsLocalDataSourceImpl settingsLocalDataSourceImpl() {
      return new SettingsLocalDataSourceImpl(provideDataStoreProvider.get(), provideKotlinSerializerProvider.get());
    }

    SettingsRepositoryImpl settingsRepositoryImpl() {
      return new SettingsRepositoryImpl(settingsLocalDataSourceImpl());
    }

    IsAutoforwardTasksSettingEnabledUseCase isAutoforwardTasksSettingEnabledUseCase() {
      return new IsAutoforwardTasksSettingEnabledUseCase(settingsRepositoryImpl());
    }

    TasksDao tasksDao() {
      return DaosModule_ProvidesTaskDaoFactory.providesTaskDao(provideDatabaseProvider.get());
    }

    ReminderDao reminderDao() {
      return DaosModule_ProvidesRemindersDaoFactory.providesRemindersDao(provideDatabaseProvider.get());
    }

    RoomTransactionRunner roomTransactionRunner() {
      return new RoomTransactionRunner(provideDatabaseProvider.get());
    }

    DefaultTasksLocalDataSource defaultTasksLocalDataSource() {
      return new DefaultTasksLocalDataSource(tasksDao(), reminderDao(), roomTransactionRunner());
    }

    DefaultTasksRepository defaultTasksRepository() {
      return new DefaultTasksRepository(defaultTasksLocalDataSource());
    }

    ObserveTasksUseCase observeTasksUseCase() {
      return new ObserveTasksUseCase(defaultTasksRepository());
    }

    TaskReminderManager taskReminderManager() {
      return ReminderManagerModule_ProvidesReminderManagerFactory.providesReminderManager(ApplicationContextModule_ProvideContextFactory.provideContext(applicationContextModule));
    }

    PopulateRecurringTasksUseCase populateRecurringTasksUseCase() {
      return new PopulateRecurringTasksUseCase(defaultTasksRepository());
    }

    DefaultNotificationNavigationIntentFactory defaultNotificationNavigationIntentFactory() {
      return new DefaultNotificationNavigationIntentFactory(ApplicationContextModule_ProvideContextFactory.provideContext(applicationContextModule));
    }

    AndroidNotificationResources androidNotificationResources() {
      return new AndroidNotificationResources(ApplicationContextModule_ProvideContextFactory.provideContext(applicationContextModule));
    }

    TaskNotificationManagerImpl taskNotificationManagerImpl() {
      return new TaskNotificationManagerImpl(ApplicationContextModule_ProvideContextFactory.provideContext(applicationContextModule), defaultNotificationNavigationIntentFactory(), androidNotificationResources());
    }

    AutoforwardTasksUseCase autoforwardTasksUseCase() {
      return new AutoforwardTasksUseCase(isAutoforwardTasksSettingEnabledUseCase(), observeTasksUseCase(), editTaskUseCaseProvider.get());
    }

    DailyReminderNotificationManagerImpl dailyReminderNotificationManagerImpl() {
      return new DailyReminderNotificationManagerImpl(ApplicationContextModule_ProvideContextFactory.provideContext(applicationContextModule), defaultNotificationNavigationIntentFactory(), androidNotificationResources());
    }

    ObserveDailyReminderUseCase observeDailyReminderUseCase() {
      return new ObserveDailyReminderUseCase(settingsRepositoryImpl());
    }

    DailyReminderAlarmSchedulerImpl dailyReminderAlarmSchedulerImpl() {
      return new DailyReminderAlarmSchedulerImpl(ApplicationContextModule_ProvideContextFactory.provideContext(applicationContextModule));
    }

    SyncDailyReminderUseCase syncDailyReminderUseCase() {
      return new SyncDailyReminderUseCase(observeDailyReminderUseCase(), dailyReminderAlarmSchedulerImpl());
    }

    RecurrenceScheduler recurrenceScheduler() {
      return new RecurrenceScheduler(ApplicationContextModule_ProvideContextFactory.provideContext(applicationContextModule));
    }

    UpdateTaskIsDoneUseCase updateTaskIsDoneUseCase() {
      return new UpdateTaskIsDoneUseCase(defaultTasksRepository(), recurrenceScheduler());
    }

    GetTaskByIdUseCase getTaskByIdUseCase() {
      return new GetTaskByIdUseCase(defaultTasksRepository());
    }

    RecurrenceManagerImpl recurrenceManagerImpl() {
      return new RecurrenceManagerImpl(defaultTasksRepository(), populateRecurringTasksUseCase());
    }

    Map mapOfStringAndProviderOfWorkerAssistedFactoryOfBuilder() {
      MapBuilder mapBuilder = MapBuilder.<String, javax.inject.Provider<WorkerAssistedFactory<? extends ListenableWorker>>>newMapBuilder(7);
      mapBuilder.put("com.costular.atomtasks.tasks.worker.AutoforwardTasksWorker", ((Provider) (autoforwardTasksWorker_AssistedFactoryProvider)));
      mapBuilder.put("com.costular.atomtasks.data.settings.dailyreminder.DailyReminderWorker", ((Provider) (dailyReminderWorker_AssistedFactoryProvider)));
      mapBuilder.put("com.costular.atomtasks.tasks.worker.MarkTaskAsDoneWorker", ((Provider) (markTaskAsDoneWorker_AssistedFactoryProvider)));
      mapBuilder.put("com.costular.atomtasks.tasks.worker.NotifyTaskWorker", ((Provider) (notifyTaskWorker_AssistedFactoryProvider)));
      mapBuilder.put("com.costular.atomtasks.tasks.worker.RecurrenceGenerationWorker", ((Provider) (recurrenceGenerationWorker_AssistedFactoryProvider)));
      mapBuilder.put("com.costular.atomtasks.tasks.worker.RestoreMissedTaskRemindersWorker", ((Provider) (restoreMissedTaskRemindersWorker_AssistedFactoryProvider)));
      mapBuilder.put("com.costular.atomtasks.tasks.worker.SetTasksRemindersWorker", ((Provider) (setTasksRemindersWorker_AssistedFactoryProvider)));
      return mapBuilder.build();
    }

    Map<String, javax.inject.Provider<WorkerAssistedFactory<? extends ListenableWorker>>> mapOfStringAndProviderOfWorkerAssistedFactoryOf(
        ) {
      return mapOfStringAndProviderOfWorkerAssistedFactoryOfBuilder();
    }

    HiltWorkerFactory hiltWorkerFactory() {
      return WorkerFactoryModule_ProvideFactoryFactory.provideFactory(mapOfStringAndProviderOfWorkerAssistedFactoryOf());
    }

    TutorialRepositoryImpl tutorialRepositoryImpl() {
      return new TutorialRepositoryImpl(provideDataStoreProvider.get());
    }

    ReviewStrategyImpl reviewStrategyImpl() {
      return new ReviewStrategyImpl(tasksDao());
    }

    LocaleResolverImpl localeResolverImpl() {
      return new LocaleResolverImpl(ApplicationContextModule_ProvideContextFactory.provideContext(applicationContextModule));
    }

    FileBackupProviderImpl fileBackupProviderImpl() {
      return new FileBackupProviderImpl(tasksDao(), reminderDao(), provideDatabaseProvider.get());
    }

    BackupRepositoryImpl backupRepositoryImpl() {
      return new BackupRepositoryImpl(fileBackupProviderImpl());
    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam,
        final DataStoreModule dataStoreModuleParam,
        final PostponeChoiceCalculatorModule postponeChoiceCalculatorModuleParam) {
      this.provideDataStoreProvider = DoubleCheck.provider(new SwitchingProvider<DataStore<Preferences>>(singletonCImpl, 1));
      this.provideKotlinSerializerProvider = DoubleCheck.provider(new SwitchingProvider<Json>(singletonCImpl, 2));
      this.provideDatabaseProvider = DoubleCheck.provider(new SwitchingProvider<AtomTasksDatabase>(singletonCImpl, 3));
      this.editTaskUseCaseProvider = DoubleCheck.provider(new SwitchingProvider<EditTaskUseCase>(singletonCImpl, 4));
      this.autoforwardTasksWorker_AssistedFactoryProvider = SingleCheck.provider(new SwitchingProvider<AutoforwardTasksWorker_AssistedFactory>(singletonCImpl, 0));
      this.dailyReminderWorker_AssistedFactoryProvider = SingleCheck.provider(new SwitchingProvider<DailyReminderWorker_AssistedFactory>(singletonCImpl, 5));
      this.markTaskAsDoneWorker_AssistedFactoryProvider = SingleCheck.provider(new SwitchingProvider<MarkTaskAsDoneWorker_AssistedFactory>(singletonCImpl, 6));
      this.notifyTaskWorker_AssistedFactoryProvider = SingleCheck.provider(new SwitchingProvider<NotifyTaskWorker_AssistedFactory>(singletonCImpl, 7));
      this.recurrenceGenerationWorker_AssistedFactoryProvider = SingleCheck.provider(new SwitchingProvider<RecurrenceGenerationWorker_AssistedFactory>(singletonCImpl, 8));
      this.restoreMissedTaskRemindersWorker_AssistedFactoryProvider = SingleCheck.provider(new SwitchingProvider<RestoreMissedTaskRemindersWorker_AssistedFactory>(singletonCImpl, 9));
      this.setTasksRemindersWorker_AssistedFactoryProvider = SingleCheck.provider(new SwitchingProvider<SetTasksRemindersWorker_AssistedFactory>(singletonCImpl, 10));
    }

    @Override
    public void injectMarkTaskAsDoneReceiver(MarkTaskAsDoneReceiver markTaskAsDoneReceiver) {
      injectMarkTaskAsDoneReceiver2(markTaskAsDoneReceiver);
    }

    @Override
    public void injectFocusApplication(FocusApplication arg0) {
      injectFocusApplication2(arg0);
    }

    @Override
    public Set<Boolean> getDisableFragmentGetContextFix() {
      return Collections.<Boolean>emptySet();
    }

    @Override
    public ActivityRetainedComponentBuilder retainedComponentBuilder() {
      return new ActivityRetainedCBuilder(singletonCImpl);
    }

    @Override
    public ServiceComponentBuilder serviceComponentBuilder() {
      return new ServiceCBuilder(singletonCImpl);
    }

    private MarkTaskAsDoneReceiver injectMarkTaskAsDoneReceiver2(MarkTaskAsDoneReceiver instance) {
      MarkTaskAsDoneReceiver_MembersInjector.injectAtomAnalytics(instance, new FirebaseAnalyticsProvider());
      return instance;
    }

    private FocusApplication injectFocusApplication2(FocusApplication instance2) {
      FocusApplication_MembersInjector.injectWorkerFactory(instance2, hiltWorkerFactory());
      return instance2;
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // com.costular.atomtasks.tasks.worker.AutoforwardTasksWorker_AssistedFactory
          return (T) new AutoforwardTasksWorker_AssistedFactory() {
            @Override
            public AutoforwardTasksWorker create(Context appContext,
                WorkerParameters workerParams) {
              return new AutoforwardTasksWorker(appContext, workerParams, singletonCImpl.autoforwardTasksUseCase());
            }
          };

          case 1: // androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
          return (T) DataStoreModule_ProvideDataStoreFactory.provideDataStore(singletonCImpl.dataStoreModule, ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), DispatcherProviderModule_ProvidesDispatcherProviderFactory.providesDispatcherProvider());

          case 2: // kotlinx.serialization.json.Json
          return (T) JsonModule_ProvideKotlinSerializerFactory.provideKotlinSerializer();

          case 3: // com.costular.atomtasks.data.database.AtomTasksDatabase
          return (T) DatabaseModule_Companion_ProvideDatabaseFactory.provideDatabase(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 4: // com.costular.atomtasks.tasks.usecase.EditTaskUseCase
          return (T) new EditTaskUseCase(singletonCImpl.defaultTasksRepository(), singletonCImpl.taskReminderManager(), singletonCImpl.populateRecurringTasksUseCase(), singletonCImpl.taskNotificationManagerImpl());

          case 5: // com.costular.atomtasks.data.settings.dailyreminder.DailyReminderWorker_AssistedFactory
          return (T) new DailyReminderWorker_AssistedFactory() {
            @Override
            public DailyReminderWorker create(Context appContext2, WorkerParameters workerParams2) {
              return new DailyReminderWorker(appContext2, workerParams2, singletonCImpl.dailyReminderNotificationManagerImpl(), singletonCImpl.syncDailyReminderUseCase(), singletonCImpl.observeDailyReminderUseCase());
            }
          };

          case 6: // com.costular.atomtasks.tasks.worker.MarkTaskAsDoneWorker_AssistedFactory
          return (T) new MarkTaskAsDoneWorker_AssistedFactory() {
            @Override
            public MarkTaskAsDoneWorker create(Context appContext3,
                WorkerParameters workerParams3) {
              return new MarkTaskAsDoneWorker(appContext3, workerParams3, singletonCImpl.updateTaskIsDoneUseCase(), singletonCImpl.taskNotificationManagerImpl());
            }
          };

          case 7: // com.costular.atomtasks.tasks.worker.NotifyTaskWorker_AssistedFactory
          return (T) new NotifyTaskWorker_AssistedFactory() {
            @Override
            public NotifyTaskWorker create(Context appContext4, WorkerParameters workerParams4) {
              return new NotifyTaskWorker(appContext4, workerParams4, singletonCImpl.getTaskByIdUseCase(), singletonCImpl.taskNotificationManagerImpl());
            }
          };

          case 8: // com.costular.atomtasks.tasks.worker.RecurrenceGenerationWorker_AssistedFactory
          return (T) new RecurrenceGenerationWorker_AssistedFactory() {
            @Override
            public RecurrenceGenerationWorker create(Context appContext5,
                WorkerParameters workerParams5) {
              return new RecurrenceGenerationWorker(appContext5, workerParams5, singletonCImpl.recurrenceManagerImpl());
            }
          };

          case 9: // com.costular.atomtasks.tasks.worker.RestoreMissedTaskRemindersWorker_AssistedFactory
          return (T) new RestoreMissedTaskRemindersWorker_AssistedFactory() {
            @Override
            public RestoreMissedTaskRemindersWorker create(Context appContext6,
                WorkerParameters workerParams6) {
              return new RestoreMissedTaskRemindersWorker(appContext6, workerParams6, singletonCImpl.observeTasksUseCase(), singletonCImpl.taskNotificationManagerImpl());
            }
          };

          case 10: // com.costular.atomtasks.tasks.worker.SetTasksRemindersWorker_AssistedFactory
          return (T) new SetTasksRemindersWorker_AssistedFactory() {
            @Override
            public SetTasksRemindersWorker create(Context appContext7,
                WorkerParameters workerParams7) {
              return new SetTasksRemindersWorker(appContext7, workerParams7, singletonCImpl.observeTasksUseCase(), singletonCImpl.taskReminderManager());
            }
          };

          default: throw new AssertionError(id);
        }
      }
    }
  }
}

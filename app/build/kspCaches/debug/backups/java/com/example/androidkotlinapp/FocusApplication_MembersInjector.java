package com.example.androidkotlinapp;

import androidx.hilt.work.HiltWorkerFactory;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

@QualifierMetadata
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
public final class FocusApplication_MembersInjector implements MembersInjector<FocusApplication> {
  private final Provider<HiltWorkerFactory> workerFactoryProvider;

  private FocusApplication_MembersInjector(Provider<HiltWorkerFactory> workerFactoryProvider) {
    this.workerFactoryProvider = workerFactoryProvider;
  }

  @Override
  public void injectMembers(FocusApplication instance) {
    injectWorkerFactory(instance, workerFactoryProvider.get());
  }

  public static MembersInjector<FocusApplication> create(
      Provider<HiltWorkerFactory> workerFactoryProvider) {
    return new FocusApplication_MembersInjector(workerFactoryProvider);
  }

  @InjectedFieldSignature("com.example.androidkotlinapp.FocusApplication.workerFactory")
  public static void injectWorkerFactory(FocusApplication instance,
      HiltWorkerFactory workerFactory) {
    instance.workerFactory = workerFactory;
  }
}

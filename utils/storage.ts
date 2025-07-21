import type { WorkoutInfo, WorkoutPlan } from '../types';

const CUSTOM_WORKOUTS_KEY = 'gen-thaimer-custom-workouts';

export const getSavedWorkouts = (): WorkoutInfo[] => {
  try {
    const saved = window.localStorage.getItem(CUSTOM_WORKOUTS_KEY);
    return saved ? JSON.parse(saved) : [];
  } catch (error) {
    console.error("Failed to load custom workouts from localStorage", error);
    // In case of parsing error, clear the corrupted data
    window.localStorage.removeItem(CUSTOM_WORKOUTS_KEY);
    return [];
  }
};

export const saveWorkout = (workoutToSave: WorkoutPlan, title: string, description: string): WorkoutInfo => {
  const customWorkouts = getSavedWorkouts();
  const newWorkout: WorkoutInfo = {
    id: `custom_${Date.now()}`,
    title,
    description,
    plan: workoutToSave,
    isCustom: true,
  };
  const updatedWorkouts = [...customWorkouts, newWorkout];
  try {
    window.localStorage.setItem(CUSTOM_WORKOUTS_KEY, JSON.stringify(updatedWorkouts));
    return newWorkout;
  } catch (error) {
    console.error("Failed to save custom workout to localStorage", error);
    throw new Error('Could not save workout. Storage might be full.');
  }
};

export const deleteWorkout = (workoutId: number | string): void => {
  let customWorkouts = getSavedWorkouts();
  customWorkouts = customWorkouts.filter(w => w.id !== workoutId);
  try {
    window.localStorage.setItem(CUSTOM_WORKOUTS_KEY, JSON.stringify(customWorkouts));
  } catch (error) {
    console.error("Failed to delete custom workout from localStorage", error);
    throw new Error('Could not delete workout.');
  }
};

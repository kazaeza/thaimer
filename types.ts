

export interface Exercise {
  name: string;
  duration: number; // in seconds
}

export enum StageType {
  WARMUP = 'WARMUP',
  WORK = 'WORK',
  REST = 'REST',
  COOLDOWN = 'COOLDOWN',
}

export interface WorkoutStage {
  type: StageType;
  name: string;
  duration: number;
  exercises: Exercise[];
}

export type WorkoutPlan = WorkoutStage[];

export interface WorkoutState {
  totalElapsedSeconds: number;
  totalWorkoutDuration: number;
  currentStageIndex: number;
  currentStage: WorkoutStage;
  currentExerciseIndex: number;
  currentExercise: Exercise;
  nextExercise: Exercise | null;
  nextStageExercise: Exercise | null;
  timeInStage: number;
  timeInExercise: number;
  timeLeftInStage: number;
  timeLeftInExercise: number;
  timeLeftInWorkout: number;
  workoutProgress: number;
  stageProgress: number;
  exerciseProgress: number;
  lap: number;
  totalLaps: number;
  isFinished: boolean;
  currentExerciseStartTime: number | null;
  nextExerciseStartTime: number | null;
}

export interface WorkoutInfo {
  id: number | string;
  title: string;
  description: string;
  plan: WorkoutPlan;
  isCustom?: boolean;
}
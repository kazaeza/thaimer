
import type { WorkoutPlan, WorkoutState, WorkoutStage, Exercise } from '../types';
import { StageType } from '../types';

export const getWorkoutState = (totalElapsedSeconds: number, plan: WorkoutPlan): WorkoutState | null => {
  const totalWorkoutDuration = plan.reduce((sum, stage) => sum + stage.duration, 0);
  let accumulatedTime = 0;
  let currentExerciseStartTime = 0;

  const totalLaps = plan.filter(stage => stage.type === StageType.WORK).length;
  let lap = 0;

  for (let stageIndex = 0; stageIndex < plan.length; stageIndex++) {
    const stage = plan[stageIndex];
    if (stage.type === StageType.WORK) {
        lap++;
    }

    const stageStartTime = accumulatedTime;
    const stageEndTime = stageStartTime + stage.duration;

    if (totalElapsedSeconds < stageEndTime) {
      const timeInStage = totalElapsedSeconds - stageStartTime;
      let accumulatedTimeInStage = 0;
      
      currentExerciseStartTime = stageStartTime;

      for (let exerciseIndex = 0; exerciseIndex < stage.exercises.length; exerciseIndex++) {
        const exercise = stage.exercises[exerciseIndex];
        const exerciseStartTime = accumulatedTimeInStage;
        const exerciseEndTime = exerciseStartTime + exercise.duration;
        
        currentExerciseStartTime = stageStartTime + exerciseStartTime;

        if (timeInStage < exerciseEndTime) {
          const timeInExercise = timeInStage - exerciseStartTime;
          
          const nextExercise = stage.exercises[exerciseIndex + 1] || null;
          
          let nextStageExercise: Exercise | null = null;
          if (!nextExercise && plan[stageIndex + 1]) {
              nextStageExercise = plan[stageIndex + 1].exercises[0] || null;
          }
          
          const nextExerciseStartTime = nextExercise 
            ? currentExerciseStartTime + exercise.duration
            : stageEndTime;
            
          return {
            totalElapsedSeconds,
            totalWorkoutDuration,
            currentStageIndex: stageIndex,
            currentStage: stage,
            currentExerciseIndex: exerciseIndex,
            currentExercise: exercise,
            nextExercise: nextExercise,
            nextStageExercise: nextStageExercise,
            timeInStage,
            timeInExercise,
            timeLeftInStage: stage.duration - timeInStage,
            timeLeftInExercise: exercise.duration - timeInExercise,
            timeLeftInWorkout: totalWorkoutDuration - totalElapsedSeconds,
            workoutProgress: totalElapsedSeconds / totalWorkoutDuration,
            stageProgress: timeInStage / stage.duration,
            exerciseProgress: timeInExercise / exercise.duration,
            lap,
            totalLaps,
            isFinished: totalElapsedSeconds >= totalWorkoutDuration,
            currentExerciseStartTime,
            nextExerciseStartTime
          };
        }
        accumulatedTimeInStage += exercise.duration;
      }
    }
    accumulatedTime += stage.duration;
  }
  
  // Handle finished state
  const lastStage = plan[plan.length - 1];
  const lastExercise = lastStage.exercises[lastStage.exercises.length - 1];
  return {
    totalElapsedSeconds: totalWorkoutDuration,
    totalWorkoutDuration,
    currentStageIndex: plan.length - 1,
    currentStage: lastStage,
    currentExerciseIndex: lastStage.exercises.length - 1,
    currentExercise: lastExercise,
    nextExercise: null,
    nextStageExercise: null,
    timeInStage: lastStage.duration,
    timeInExercise: lastExercise.duration,
    timeLeftInStage: 0,
    timeLeftInExercise: 0,
    timeLeftInWorkout: 0,
    workoutProgress: 1,
    stageProgress: 1,
    exerciseProgress: 1,
    lap: totalLaps,
    totalLaps,
    isFinished: true,
    currentExerciseStartTime: totalWorkoutDuration - lastExercise.duration,
    nextExerciseStartTime: null,
  };
};

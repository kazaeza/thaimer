import React, { useEffect, useRef } from 'react';
import type { WorkoutState } from '../types';
import { formatTime } from '../utils/time';
import CircularProgress from './CircularProgress';
import Controls from './Controls';
import type { useAudio } from '../hooks/useAudio';
import { useLanguage } from '../contexts/LanguageContext';

interface WorkoutScreenProps {
  workoutState: WorkoutState;
  isPaused: boolean;
  onPlayPause: () => void;
  onNext: () => void;
  onPrev: () => void;
  onToggleSummary: () => void;
  onBack: () => void;
  audio: ReturnType<typeof useAudio>;
}

const Stat: React.FC<{ label: string; value: string | number }> = ({ label, value }) => (
  <div className="text-center">
    <p className="text-xs text-gray-500 uppercase tracking-widest">{label}</p>
    <p className="text-2xl font-mono font-semibold text-gray-800">{value}</p>
  </div>
);

const NextUp: React.FC<{ exercise1?: string | null; exercise2?: string | null }> = ({ exercise1, exercise2 }) => {
    const { t } = useLanguage();
    return (
      <div className="text-center my-4 h-16 flex flex-col justify-center">
        {exercise1 && (
          <>
            <p className="text-xs text-purple-500 uppercase font-bold tracking-widest">{t('Next Up')}</p>
            <p className="text-xl font-semibold text-gray-700">{t(exercise1)}</p>
            {exercise2 && <p className="text-md text-blue-400">{t(exercise2)}</p>}
          </>
        )}
      </div>
    );
};


const WorkoutScreen: React.FC<WorkoutScreenProps> = ({ workoutState, isPaused, onPlayPause, onNext, onPrev, onToggleSummary, onBack, audio }) => {
  const {
    totalElapsedSeconds,
    lap,
    totalLaps,
    timeLeftInWorkout,
    currentStage,
    stageProgress,
    timeLeftInExercise,
    currentExercise,
    nextExercise,
    nextStageExercise
  } = workoutState;
  
  const { t } = useLanguage();
  const isInitialMount = useRef(true);

  useEffect(() => {
    if (isInitialMount.current) {
      isInitialMount.current = false;
      return;
    }
    if (!isPaused) {
       audio.playStartSound();
    }
  }, [currentExercise, isPaused]);

  useEffect(() => {
    if (!isPaused && timeLeftInExercise > 0 && timeLeftInExercise <= 3) {
      audio.playUrgentSound();
    }
  }, [timeLeftInExercise, isPaused]);


  let nextUp1: string | null = null;
  let nextUp2: string | null = null;

  if (nextExercise) {
    nextUp1 = nextExercise.name;
    const nextExIdx = currentStage.exercises.findIndex(e => e.name === nextExercise.name);
    if (currentStage.exercises[nextExIdx+1]) {
        nextUp2 = currentStage.exercises[nextExIdx+1].name;
    } else if (nextStageExercise) {
        nextUp2 = nextStageExercise.name;
    }
  } else if (nextStageExercise) {
      nextUp1 = nextStageExercise.name;
  }
  
  return (
    <main className="flex flex-col justify-between items-center h-full w-full py-6 px-4">
      <header className="w-full flex justify-between items-center px-4">
        <Stat label={t('Elapsed Time')} value={formatTime(totalElapsedSeconds)} />
        <Stat label={t('Laps')} value={`${lap}/${totalLaps}`} />
        <Stat label={t('Time Left')} value={formatTime(timeLeftInWorkout)} />
      </header>

      <section className="flex flex-col items-center justify-center flex-grow">
        <NextUp exercise1={nextUp1} exercise2={nextUp2} />
        <CircularProgress
          stage={currentStage}
          stageProgress={stageProgress}
          timeLeftInExercise={timeLeftInExercise}
          currentExerciseName={currentExercise.name}
        />
      </section>

      <footer className="w-full">
        <Controls 
          isPaused={isPaused} 
          onPlayPause={onPlayPause} 
          onNext={onNext}
          onPrev={onPrev}
          onToggleSummary={onToggleSummary}
          onBack={onBack}
        />
      </footer>
    </main>
  );
};

export default WorkoutScreen;
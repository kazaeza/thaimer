import React, { useState, useEffect, useCallback } from 'react';
import type { WorkoutPlan } from './types';
import { getWorkoutState } from './utils/state.ts';
import WorkoutSetup from './components/WorkoutSetup';
import WorkoutScreen from './components/WorkoutScreen';
import WorkoutSelectionScreen from './components/WorkoutSelectionScreen';
import WorkoutGeneratorScreen from './components/WorkoutGeneratorScreen';
import { useAudio } from './hooks/useAudio';
import { LanguageContext, Language } from './contexts/LanguageContext';
import { translations } from './constants/translations';

const App: React.FC = () => {
  const [selectedWorkoutPlan, setSelectedWorkoutPlan] = useState<WorkoutPlan | null>(null);
  const [showGenerator, setShowGenerator] = useState(false);
  const [isStarted, setIsStarted] = useState(false);
  const [isPaused, setIsPaused] = useState(true);
  const [showSummary, setShowSummary] = useState(false);
  const [totalElapsedSeconds, setTotalElapsedSeconds] = useState(0);
  const [language, setLanguage] = useState<Language>('en');
  
  const audio = useAudio();

  const t = useCallback((key: string | null | undefined): string => {
    if (!key) return '';
    if (language === 'en') {
        return key; // English is the key
    }
    return translations[key]?.[language] || key;
  }, [language]);

  const totalWorkoutDuration = selectedWorkoutPlan 
    ? selectedWorkoutPlan.reduce((sum, stage) => sum + stage.duration, 0)
    : 0;

  useEffect(() => {
    if (!isStarted || isPaused) {
      return;
    }

    const interval = setInterval(() => {
      setTotalElapsedSeconds(prevTime => {
        if (prevTime >= totalWorkoutDuration) {
          setIsPaused(true);
          return totalWorkoutDuration;
        }
        return prevTime + 1;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [isStarted, isPaused, totalWorkoutDuration]);

  const handleSelectWorkout = (plan: WorkoutPlan) => {
    setSelectedWorkoutPlan(plan);
    setIsStarted(false); // Go to setup screen first
  };

  const handleStartGeneratedWorkout = (plan: WorkoutPlan) => {
    setSelectedWorkoutPlan(plan);
    setShowGenerator(false);
    setIsStarted(false);
  };

  const handleBackToSelection = () => {
    setSelectedWorkoutPlan(null);
    setIsStarted(false);
    setTotalElapsedSeconds(0);
    setIsPaused(true);
    setShowGenerator(false); // Ensure generator is hidden
  };

  const handleStart = () => {
    audio.init();
    setTotalElapsedSeconds(0);
    setIsStarted(true);
    setIsPaused(false);
    setShowSummary(false);
  };

  const handlePlayPause = () => {
    if (totalElapsedSeconds >= totalWorkoutDuration) return;
    setIsPaused(prev => !prev);
  };

  const handleSkipToTime = (time: number) => {
    setTotalElapsedSeconds(Math.max(0, Math.min(time, totalWorkoutDuration)));
  };

  const renderApp = () => {
    if (showGenerator) {
      return (
        <WorkoutGeneratorScreen 
          onBack={() => setShowGenerator(false)} 
          onStartWorkout={handleStartGeneratedWorkout} 
        />
      );
    }

    if (!selectedWorkoutPlan) {
      return <WorkoutSelectionScreen 
        onSelect={handleSelectWorkout}
        onStartGenerator={() => setShowGenerator(true)} 
      />;
    }

    const workoutState = getWorkoutState(totalElapsedSeconds, selectedWorkoutPlan);
    
    if (!workoutState) {
      return (
        <div className="bg-black text-white h-screen flex flex-col items-center justify-center">
          <p>Workout finished or invalid state.</p>
          <button onClick={handleBackToSelection} className="mt-4 px-4 py-2 bg-orange-500 rounded-lg">Back to Workouts</button>
        </div>
      );
    }

    const handleNext = () => {
        if(workoutState.nextExerciseStartTime !== null) {
            handleSkipToTime(workoutState.nextExerciseStartTime);
        }
    };

    const handlePrev = () => {
        if(workoutState.currentExerciseStartTime !== null) {
            handleSkipToTime(workoutState.currentExerciseStartTime - 1);
        }
    };

    const toggleSummary = () => {
      setShowSummary(!showSummary);
      if (!showSummary) {
        setIsPaused(true);
      }
    };
    
    if (!isStarted) {
      return <WorkoutSetup workoutPlan={selectedWorkoutPlan} onStart={handleStart} onBack={handleBackToSelection}/>;
    }

    return (
      <div className="bg-black text-white min-h-screen flex items-center justify-center font-sans">
        <div className="w-full max-w-sm h-screen bg-white text-gray-800 flex flex-col">
          {showSummary ? (
              <WorkoutSetup 
                  workoutPlan={selectedWorkoutPlan} 
                  onStart={() => {
                      setShowSummary(false);
                      setIsPaused(false);
                  }}
                  isWorkoutInProgress={true}
                  onBack={() => setShowSummary(false)}
              />
          ) : (
              <WorkoutScreen
                workoutState={workoutState}
                isPaused={isPaused}
                onPlayPause={handlePlayPause}
                onNext={handleNext}
                onPrev={handlePrev}
                onToggleSummary={toggleSummary}
                onBack={handleBackToSelection}
                audio={audio}
              />
          )}
        </div>
      </div>
    );
  };

  return (
    <LanguageContext.Provider value={{ language, setLanguage, t }}>
      {renderApp()}
    </LanguageContext.Provider>
  );
};

export default App;
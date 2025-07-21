import React from 'react';
import type { WorkoutPlan } from '../types';
import { StageType } from '../types';
import { formatTime } from '../utils/time';
import { BackIcon } from './icons';
import { useLanguage } from '../contexts/LanguageContext';

interface WorkoutSetupProps {
  workoutPlan: WorkoutPlan;
  onStart: () => void;
  onBack: () => void;
  isWorkoutInProgress?: boolean;
}

const WorkoutSetup: React.FC<WorkoutSetupProps> = ({ workoutPlan, onStart, onBack, isWorkoutInProgress = false }) => {
  const { t } = useLanguage();
  const buttonText = isWorkoutInProgress ? t('Resume Workout') : t('Start Workout');
  const backLabel = isWorkoutInProgress ? t('Back to Workout') : t('Back to selection');

  return (
    <div className="flex flex-col h-full bg-white p-6">
       <header className="relative flex items-center justify-center mb-4">
            <button 
                onClick={onBack} 
                className="absolute left-0 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-800"
                aria-label={backLabel}
            >
                <BackIcon className="w-8 h-8" />
            </button>
            <h1 className="text-3xl font-bold text-gray-800 text-center">{t('Thai Boxer HIIT')}</h1>
      </header>
      <p className="text-gray-500 text-center mb-6">{t('Workout Summary')}</p>
      
      <div className="flex-grow overflow-y-auto pr-2">
        {workoutPlan.map((stage, index) => (
          <div key={index} className="mb-4">
            <h2 className={`text-xl font-bold ${
              stage.type === StageType.WORK ? 'text-orange-500' : 
              stage.type === StageType.REST ? 'text-blue-500' : 'text-gray-600'
            }`}>
              {t(stage.name)} <span className="text-sm font-normal text-gray-500">({formatTime(stage.duration)})</span>
            </h2>
            {stage.type !== StageType.REST && (
              <ul className="list-disc list-inside pl-2 text-gray-700">
                {stage.exercises.map((exercise, exIndex) => (
                  <li key={exIndex}>{t(exercise.name)}</li>
                ))}
              </ul>
            )}
          </div>
        ))}
      </div>

      <div className="pt-4 mt-auto">
        <button
          onClick={onStart}
          className="w-full bg-orange-500 text-white font-bold py-4 px-4 rounded-full text-2xl hover:bg-orange-600 transition-colors focus:outline-none focus:ring-2 focus:ring-orange-400 focus:ring-opacity-75"
        >
          {buttonText}
        </button>
      </div>
    </div>
  );
};

export default WorkoutSetup;
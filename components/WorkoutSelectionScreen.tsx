import React, { useState, useEffect } from 'react';
import type { WorkoutPlan, WorkoutInfo } from '../types';
import { useLanguage } from '../contexts/LanguageContext';
import { SparklesIcon, TrashIcon } from './icons';
import { WORKOUTS } from '../constants/workouts';
import { getSavedWorkouts, deleteWorkout } from '../utils/storage';

interface WorkoutSelectionScreenProps {
  onSelect: (plan: WorkoutPlan) => void;
  onStartGenerator: () => void;
}

const WorkoutSelectionScreen: React.FC<WorkoutSelectionScreenProps> = ({ onSelect, onStartGenerator }) => {
  const { language, setLanguage, t } = useLanguage();
  const [customWorkouts, setCustomWorkouts] = useState<WorkoutInfo[]>([]);

  useEffect(() => {
    setCustomWorkouts(getSavedWorkouts());
  }, []);

  const handleDelete = (workoutId: number | string) => {
    if (window.confirm(t('Are you sure you want to delete this workout?'))) {
        deleteWorkout(workoutId);
        setCustomWorkouts(current => current.filter(w => w.id !== workoutId));
    }
  };


  return (
    <div className="flex flex-col h-screen bg-white p-6 font-sans">
      <header className="relative flex items-center justify-between mb-6">
        <div className="w-16"></div> {/* Spacer */}
        <div className="text-center">
            <h1 className="text-3xl font-bold text-gray-800">{t('Thai Boxer HIIT')}</h1>
            <p className="text-gray-500">{t('Select Your Workout')}</p>
        </div>
        <div className="flex items-center space-x-1 p-1 rounded-full bg-gray-200 w-20">
            <button
                onClick={() => setLanguage('en')}
                className={`w-1/2 text-xs font-bold rounded-full transition-colors ${language === 'en' ? 'bg-orange-500 text-white shadow' : 'text-gray-600'}`}
                aria-pressed={language === 'en'}
            >
                EN
            </button>
            <button
                onClick={() => setLanguage('ru')}
                className={`w-1/2 text-xs font-bold rounded-full transition-colors ${language === 'ru' ? 'bg-orange-500 text-white shadow' : 'text-gray-600'}`}
                aria-pressed={language === 'ru'}
            >
                RU
            </button>
        </div>
      </header>
      
      <div className="px-1 mb-4">
        <button
          onClick={onStartGenerator}
          className="w-full bg-gradient-to-r from-orange-500 to-purple-600 text-white p-4 rounded-lg text-left flex items-center space-x-4 hover:shadow-lg transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-purple-500"
        >
          <SparklesIcon className="w-8 h-8 flex-shrink-0" />
          <div>
            <h2 className="text-xl font-bold">{t('Workout Generator')}</h2>
            <p className="text-sm opacity-90">{t('Create your custom workout with AI')}</p>
          </div>
        </button>
      </div>

      <div className="flex-grow overflow-y-auto space-y-4">
        {/* Custom Workouts Section */}
        <div className="mb-6">
            <h2 className="text-sm font-bold uppercase text-gray-500 mb-2 px-1">{t('My Saved Workouts')}</h2>
            {customWorkouts.length > 0 ? (
                <div className="space-y-3">
                    {customWorkouts.map((workout) => (
                    <div key={workout.id} className="relative group">
                        <button
                        onClick={() => onSelect(workout.plan)}
                        className="w-full bg-orange-50 p-4 rounded-lg text-left hover:bg-orange-100 hover:shadow-md transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-orange-500 pr-12"
                        >
                        <h2 className="text-xl font-bold text-gray-800">{workout.title}</h2>
                        <p className="text-gray-600 text-sm">{workout.description}</p>
                        </button>
                        <button
                        onClick={() => handleDelete(workout.id)}
                        className="absolute right-2 top-1/2 -translate-y-1/2 p-2 rounded-full text-gray-400 hover:bg-red-100 hover:text-red-500 opacity-0 group-hover:opacity-100 transition-opacity"
                        aria-label={t('Delete')}
                        >
                        <TrashIcon className="w-5 h-5" />
                        </button>
                    </div>
                    ))}
                </div>
            ) : (
                <p className="text-gray-500 text-center py-4">{t('No saved workouts yet.')}</p>
            )}
        </div>

        {/* Preset Workouts Section */}
        <div className="mb-6">
            <h2 className="text-sm font-bold uppercase text-gray-500 mb-2 px-1">{t('Preset Workouts')}</h2>
            <div className="space-y-3">
                {WORKOUTS.map((workout) => (
                    <button
                        key={workout.id}
                        onClick={() => onSelect(workout.plan)}
                        className="w-full bg-gray-100 p-4 rounded-lg text-left hover:bg-orange-100 hover:shadow-md transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-orange-500"
                    >
                        <h2 className="text-xl font-bold text-gray-800">{t(workout.title)}</h2>
                        <p className="text-gray-600">{t(workout.description)}</p>
                    </button>
                ))}
            </div>
        </div>
      </div>
    </div>
  );
};

export default WorkoutSelectionScreen;
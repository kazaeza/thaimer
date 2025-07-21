import React from 'react';
import { formatTime } from '../utils/time';
import type { WorkoutStage } from '../types';
import { StageType } from '../types';
import { useLanguage } from '../contexts/LanguageContext';

interface CircularProgressProps {
  stage: WorkoutStage;
  stageProgress: number;
  timeLeftInExercise: number;
  currentExerciseName: string;
}

const STROKE_WIDTH = 12;
const RADIUS = 100;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

const colors = [
  'stroke-blue-400',
  'stroke-purple-500',
  'stroke-pink-500',
  'stroke-yellow-400',
  'stroke-teal-400',
  'stroke-indigo-500',
];

const Arc: React.FC<{ progress: number; rotation: number; color: string }> = ({ progress, rotation, color }) => {
  const arcLength = progress * CIRCUMFERENCE;
  return (
    <circle
      cx="110"
      cy="110"
      r={RADIUS}
      fill="none"
      strokeWidth={STROKE_WIDTH}
      className={color}
      strokeDasharray={`${arcLength} ${CIRCUMFERENCE - arcLength}`}
      transform={`rotate(${rotation} 110 110)`}
    />
  );
};


const CircularProgress: React.FC<CircularProgressProps> = ({
  stage,
  stageProgress,
  timeLeftInExercise,
  currentExerciseName,
}) => {
  const { t } = useLanguage();
  const offset = CIRCUMFERENCE - stageProgress * CIRCUMFERENCE;
  
  let accumulatedProgress = 0;

  return (
    <div className="relative w-72 h-72">
      <svg className="w-full h-full" viewBox="0 0 220 220">
        <g transform="rotate(-90 110 110)">
          {/* Base Circle */}
          <circle
            cx="110"
            cy="110"
            r={RADIUS}
            fill="none"
            strokeWidth={STROKE_WIDTH}
            className="stroke-gray-200"
          />
          
          {/* Segments for work stages */}
          {stage.type === StageType.WORK && stage.exercises.map((exercise, index) => {
            const exerciseProgress = exercise.duration / stage.duration;
            const rotation = accumulatedProgress * 360;
            accumulatedProgress += exerciseProgress;
            return (
              <Arc 
                key={index}
                progress={exerciseProgress} 
                rotation={rotation}
                color={colors[index % colors.length]}
              />
            );
          })}

          {/* Main Progress Circle */}
          <circle
            cx="110"
            cy="110"
            r={RADIUS}
            fill="none"
            strokeWidth={STROKE_WIDTH + 1} // Slightly thicker to overlay nicely
            className="stroke-orange-500 transition-all duration-1000 linear"
            strokeDasharray={CIRCUMFERENCE}
            strokeDashoffset={offset}
            strokeLinecap="round"
          />
        </g>
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center text-center">
        <h2 className="text-3xl font-bold text-orange-500 leading-tight px-4">{t(currentExerciseName)}</h2>
        <p className="text-6xl font-mono font-bold text-gray-800 tracking-tighter mt-2">
          {formatTime(timeLeftInExercise)}
        </p>
      </div>
    </div>
  );
};

export default CircularProgress;
import type { WorkoutPlan, WorkoutInfo } from '../types';
import { StageType } from '../types';

/*
Exercise Glossary for context:
High Knees: Бег на месте с высоким подъемом коленей, до уровня талии.
Mountain Climbers: Из упора лежа (позиция для отжиманий) поочередно и быстро подтягивайте колени к груди.
Burpees: Присядьте, поставьте руки на пол, прыжком перейдите в упор лежа, вернитесь в присед и выпрыгните вверх.
Tuck Jumps: Вертикальный прыжок с подтягиванием коленей максимально близко к груди.
Jumping Lunges: Выпады со сменой ног в прыжке.
Explosive Push-ups: Отжимания, выполняемые с такой силой, что ладони на мгновение отрываются от пола.
Plank: Удержание корпуса в прямой линии, стоя в упоре на локтях или прямых руках.
Shadow Boxing (SB): Бой с тенью. Имитация боя с воображаемым противником.
*/


const WARMUP_STAGE: WorkoutPlan[0] = {
  type: StageType.WARMUP,
  name: 'Warm-up',
  duration: 300,
  exercises: [
    { name: 'Light Jump Rope', duration: 120 },
    { name: 'Joint Mobility', duration: 180 },
  ],
};

const COOLDOWN_STAGE: WorkoutPlan[0] = {
  type: StageType.COOLDOWN,
  name: 'Cool-down',
  duration: 300,
  exercises: [
    { name: 'Cooldown Walk', duration: 120 },
    { name: 'Static Stretching', duration: 180 },
  ],
};

const REST_60S: WorkoutPlan[0] = { type: StageType.REST, name: 'Rest', duration: 60, exercises: [{ name: 'Rest', duration: 60 }] };
const REST_90S: WorkoutPlan[0] = { type: StageType.REST, name: 'Rest', duration: 90, exercises: [{ name: 'Rest', duration: 90 }] };
const REST_120S: WorkoutPlan[0] = { type: StageType.REST, name: 'Rest', duration: 120, exercises: [{ name: 'Rest', duration: 120 }] };

// --- WORKOUT DEFINITIONS ---

const DAY_1_PLAN: WorkoutPlan = [
  { type: StageType.WORK, name: 'Round 1', duration: 180, exercises: [{ name: 'Jump Rope', duration: 60 }, { name: 'Shadow Boxing', duration: 120 }] },
  REST_60S,
  { type: StageType.WORK, name: 'Round 2', duration: 180, exercises: [{ name: 'Hand Shadowboxing', duration: 60 }, { name: 'Push-ups', duration: 60 }, { name: 'Plank', duration: 60 }] },
  REST_60S,
  { type: StageType.WORK, name: 'Round 3', duration: 180, exercises: [{ name: 'Thai Jump Rope', duration: 60 }, { name: 'Leg Shadowboxing', duration: 120 }] },
  REST_60S,
  { type: StageType.WORK, name: 'Round 4', duration: 180, exercises: [{ name: 'Squats', duration: 60 }, { name: 'Alternating Lunges', duration: 60 }, { name: 'Shadow Boxing', duration: 60 }] },
  REST_60S,
  { type: StageType.WORK, name: 'Round 5', duration: 180, exercises: [{ name: 'Interval Jump Rope', duration: 180 }] },
  REST_60S,
  { type: StageType.WORK, name: 'Round 6', duration: 180, exercises: [{ name: 'Combo Shadowboxing', duration: 180 }] },
  REST_60S,
  { type: StageType.WORK, name: 'Round 7', duration: 180, exercises: [{ name: 'Burpees', duration: 60 }, { name: 'Thai Jump Rope', duration: 60 }, { name: 'Max Speed SB', duration: 60 }] },
];

const DAY_2_CIRCUIT_STAGE: WorkoutPlan[0] = {
  type: StageType.WORK,
  name: 'Strength Circuit',
  duration: 225,
  exercises: [
    { name: 'Pull-ups', duration: 45 },
    { name: 'Push-ups', duration: 45 },
    { name: 'Squats', duration: 45 },
    { name: 'Burpees', duration: 45 },
    { name: 'Plank', duration: 45 },
  ],
};
const DAY_2_PLAN: WorkoutPlan = [
  DAY_2_CIRCUIT_STAGE, REST_90S,
  DAY_2_CIRCUIT_STAGE, REST_90S,
  DAY_2_CIRCUIT_STAGE, REST_90S,
  DAY_2_CIRCUIT_STAGE, REST_90S,
  DAY_2_CIRCUIT_STAGE,
];

const DAY_3_BLOCK_1_EXERCISES = [
    { name: 'Jump Squats', duration: 40 }, { name: 'Rest', duration: 20 },
    { name: 'Explosive Push-ups', duration: 40 }, { name: 'Rest', duration: 20 },
    { name: 'High Knee Thai Rope', duration: 40 }, { name: 'Rest', duration: 20 },
    { name: 'Speed Shadowboxing', duration: 40 }, { name: 'Rest', duration: 20 },
];
const DAY_3_BLOCK_2_EXERCISES = [
    { name: 'Jumping Lunges', duration: 40 }, { name: 'Rest', duration: 20 },
    { name: 'Mountain Climbers', duration: 40 }, { name: 'Rest', duration: 20 },
    { name: 'Speed Jab-Cross', duration: 40 }, { name: 'Rest', duration: 20 },
    { name: 'Tuck Jumps', duration: 40 }, { name: 'Rest', duration: 20 },
];
const DAY_3_PLAN: WorkoutPlan = [
    { type: StageType.WORK, name: 'HIIT Block 1', duration: 720, exercises: [...DAY_3_BLOCK_1_EXERCISES, ...DAY_3_BLOCK_1_EXERCISES, ...DAY_3_BLOCK_1_EXERCISES]},
    REST_120S,
    { type: StageType.WORK, name: 'HIIT Block 2', duration: 720, exercises: [...DAY_3_BLOCK_2_EXERCISES, ...DAY_3_BLOCK_2_EXERCISES, ...DAY_3_BLOCK_2_EXERCISES]},
    { type: StageType.WORK, name: 'Finish', duration: 120, exercises: [{ name: 'Easy Jump Rope', duration: 120 }] },
];

const DAY_4_PLAN: WorkoutPlan = [
    { type: StageType.WORK, name: 'Technique & Recovery', duration: 1800, exercises: [
        { name: 'Slow Shadowboxing', duration: 900 },
        { name: 'One-Leg Stand (L)', duration: 120 },
        { name: 'One-Leg Stand (R)', duration: 120 },
        { name: 'Defensive Drills', duration: 300 },
        { name: 'Mobility & Deep Stretch', duration: 360 },
    ]},
];

const DAY_5_PLAN: WorkoutPlan = [
    { type: StageType.WORK, name: 'Strength Pyramid', duration: 1200, exercises: [{ name: 'Pyramid: Pull-up / Push-up', duration: 1200 }] },
    { type: StageType.WORK, name: 'Core Finisher', duration: 600, exercises: [
        { name: 'Leg Raises', duration: 60 }, { name: 'Plank', duration: 60 }, { name: 'Side Plank Left', duration: 60 }, { name: 'Side Plank Right', duration: 60 }, REST_60S.exercises[0],
        { name: 'Leg Raises', duration: 60 }, { name: 'Plank', duration: 60 }, { name: 'Side Plank Left', duration: 60 }, { name: 'Side Plank Right', duration: 60 }, REST_60S.exercises[0],
    ] },
];

const DAY_6_CIRCUIT_STAGE: WorkoutPlan[0] = {
    type: StageType.WORK,
    name: 'Plyo & Speed Circuit',
    duration: 195,
    exercises: [
        { name: 'Tuck Jumps', duration: 45 },
        { name: 'High Knees', duration: 45 },
        { name: 'Speed Jab-Cross', duration: 60 },
        { name: 'Mountain Climbers', duration: 45 },
    ],
};
const DAY_6_PLAN: WorkoutPlan = [
    DAY_6_CIRCUIT_STAGE, REST_60S,
    DAY_6_CIRCUIT_STAGE, REST_60S,
    DAY_6_CIRCUIT_STAGE, REST_60S,
    DAY_6_CIRCUIT_STAGE, REST_60S,
    DAY_6_CIRCUIT_STAGE
];

const DAY_7_PLAN: WorkoutPlan = [
  { type: StageType.WORK, name: 'Round 1', duration: 180, exercises: [{ name: 'Fast Jump Rope', duration: 60 }, { name: 'Speed Shadowboxing', duration: 60 }, { name: 'Burpees', duration: 60 }] },
  REST_60S,
  { type: StageType.WORK, name: 'Round 2', duration: 180, exercises: [{ name: 'Thai Jump Rope', duration: 60 }, { name: 'Max Reps Pull-ups', duration: 60 }, { name: 'Max Reps Push-ups', duration: 60 }] },
  REST_60S,
  { type: StageType.WORK, name: 'Round 3', duration: 180, exercises: [{ name: 'Kicks & Knees SB', duration: 60 }, { name: 'Jump Squats', duration: 60 }, { name: 'Kicks & Knees SB', duration: 60 }] },
  REST_60S,
  { type: StageType.WORK, name: 'Round 4', duration: 180, exercises: [{ name: 'Clinch & Elbows SB', duration: 60 }, { name: 'Mountain Climbers', duration: 60 }, { name: 'Clinch & Elbows SB', duration: 60 }] },
  REST_60S,
  { type: StageType.WORK, name: 'Round 5', duration: 180, exercises: [{ name: 'Death by Burpee', duration: 180 }] },
  REST_60S,
  { type: StageType.WORK, name: 'Round 6', duration: 180, exercises: [{ name: 'Fast Jump Rope', duration: 60 }, { name: 'Hand Shadowboxing', duration: 60 }, { name: 'Plank', duration: 60 }] },
  REST_60S,
  { type: StageType.WORK, name: 'Round 7', duration: 180, exercises: [{ name: 'Final Shadowboxing (All out!)', duration: 180 }] },
];

export const WORKOUTS: WorkoutInfo[] = [
    { id: 1, title: 'Day 1: Classic Endurance', description: '7 rounds of boxing essentials.', plan: [WARMUP_STAGE, ...DAY_1_PLAN, COOLDOWN_STAGE] },
    { id: 2, title: 'Day 2: Strength Circuit', description: '5 rounds of full-body strength exercises.', plan: [WARMUP_STAGE, ...DAY_2_PLAN, COOLDOWN_STAGE] },
    { id: 3, title: 'Day 3: Explosive HIIT', description: 'High-intensity intervals for explosive power.', plan: [WARMUP_STAGE, ...DAY_3_PLAN, COOLDOWN_STAGE] },
    { id: 4, title: 'Day 4: Technique & Recovery', description: 'Low-intensity work on form and mobility.', plan: [WARMUP_STAGE, ...DAY_4_PLAN, COOLDOWN_STAGE] },
    { id: 5, title: 'Day 5: Strength Pyramid', description: 'Climb the ladder of strength and finish with core.', plan: [WARMUP_STAGE, ...DAY_5_PLAN, COOLDOWN_STAGE] },
    { id: 6, title: 'Day 6: Plyo & Speed', description: '5 rounds focused on explosive movements.', plan: [WARMUP_STAGE, ...DAY_6_PLAN, COOLDOWN_STAGE] },
    { id: 7, title: 'Day 7: Championship Rounds', description: '7 rounds at maximum intensity to finish the week.', plan: [WARMUP_STAGE, ...DAY_7_PLAN, COOLDOWN_STAGE] },
];
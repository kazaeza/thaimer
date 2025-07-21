import { GoogleGenAI, Type } from '@google/genai';
import type { WorkoutPlan } from '../types';

const exerciseSchema = {
    type: Type.OBJECT,
    properties: {
        name: { type: Type.STRING, description: 'Name of the exercise (e.g., "Jump Rope").' },
        duration: { type: Type.INTEGER, description: 'Duration of the exercise in seconds.' },
    },
    required: ['name', 'duration']
};

const workoutStageSchema = {
    type: Type.OBJECT,
    properties: {
        type: { 
            type: Type.STRING, 
            description: "Type of the stage. Must be one of: 'WARMUP', 'WORK', 'REST', 'COOLDOWN'.",
            enum: ['WARMUP', 'WORK', 'REST', 'COOLDOWN']
        },
        name: { type: Type.STRING, description: 'Name of the stage (e.g., "Round 1", "Warm-up").' },
        duration: { type: Type.INTEGER, description: 'Total duration of the stage in seconds. MUST be the sum of durations of all exercises within this stage.' },
        exercises: {
            type: Type.ARRAY,
            description: 'An array of exercises for this stage.',
            items: exerciseSchema
        }
    },
    required: ['type', 'name', 'duration', 'exercises']
};

const workoutPlanSchema = {
    type: Type.ARRAY,
    description: 'The full workout plan, which is an array of workout stages.',
    items: workoutStageSchema
};

const SYSTEM_INSTRUCTION = `You are an expert fitness coach specializing in creating high-intensity interval training (HIIT) workouts for Thai Boxing. Your goal is to generate a complete workout plan in JSON format based on the user's request.

**RULES:**
1.  **JSON Output Only:** Your entire response must be a single, valid JSON object that conforms to the provided schema. Do not include any explanatory text, markdown, or anything outside of the JSON structure.
2.  **Structure:** The top-level object is an array of "Workout Stages".
3.  **Stages:** Every plan must include a \`WARMUP\` stage at the beginning and a \`COOLDOWN\` stage at the end. Between them, you can have \`WORK\` and \`REST\` stages.
4.  **Durations:** The \`duration\` of a stage MUST be the exact sum of the \`duration\` of all its exercises. This is critical. For \`REST\` stages, there is usually one exercise called "Rest" with a duration matching the stage duration.
5.  **Exercise Naming:** Use clear, concise exercise names in English (e.g., 'Jump Rope', 'Shadow Boxing', 'Burpees'). The app will handle translations.
6.  **Safety First:** If the user mentions an injury (e.g., "twisted ankle," "sore shoulder"), avoid exercises that would strain that body part. For an ankle injury, avoid jumping, running, etc.
`;

export async function generateWorkoutFromGemini(prompt: string): Promise<WorkoutPlan> {
    const apiKey = process.env.API_KEY;

    if (!apiKey || apiKey.length < 30) { 
      console.error("API key is not configured correctly in the build environment.");
      throw new Error(
        "Ключ API не настроен. Убедитесь, что вы создали переменную 'API_KEY' в настройках вашего сайта на Netlify (Site settings > Build & deploy > Environment) и перезапустили деплой."
      );
    }

    try {
        const ai = new GoogleGenAI({ apiKey });
        
        const response = await ai.models.generateContent({
            model: 'gemini-2.5-flash',
            contents: prompt,
            config: {
                systemInstruction: SYSTEM_INSTRUCTION,
                responseMimeType: "application/json",
                responseSchema: workoutPlanSchema,
            }
        });

        const jsonText = response.text.trim();
        if (!jsonText) {
            throw new Error('API вернул пустой ответ.');
        }

        const generatedPlan = JSON.parse(jsonText);

        if (!Array.isArray(generatedPlan)) {
            throw new Error('Не удалось разобрать план тренировки. Попробуйте уточнить ваш запрос.');
        }
        
        return generatedPlan as WorkoutPlan;

    } catch (error) {
        console.error("Error generating workout with Gemini:", error);

        if (error instanceof Error && (error.message.includes('API key not valid') || error.message.includes('invalid'))) {
            throw new Error(
              "Ключ API недействителен. Проверьте его в настройках Netlify (Site settings > Build & deploy > Environment) и убедитесь, что он скопирован правильно, без лишних символов."
            );
        }
        if (error instanceof SyntaxError) {
            throw new Error("Не удалось обработать план тренировки. Попробуйте уточнить ваш запрос.");
        }
        
        throw error;
    }
}
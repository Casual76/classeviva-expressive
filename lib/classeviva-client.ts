/**
 * Classeviva API Client
 * Unofficial client per l'integrazione con il registro elettronico Classeviva
 * 
 * Basato su reverse engineering degli endpoint ufficiali
 * Documentazione: https://github.com/michelangelomo/Classeviva-Official-Endpoints
 */

import axios, { AxiosInstance } from "axios";

const API_BASE_URL = "https://web.spaggiari.eu/rest/v1";
// API Key pubblica di Classeviva (da documentazione ufficiale)
const API_KEY = "+zorro+";

export interface LoginCredentials {
  username: string;
  password: string;
}

export interface AuthToken {
  token: string;
  expiresAt: number;
}

export interface StudentProfile {
  id: string;
  name: string;
  surname: string;
  email: string;
  class: string;
  section: string;
  school: string;
  schoolYear: string;
}

export interface Grade {
  id: string;
  subject: string;
  grade: number | string;
  description?: string;
  date: string;
  type: string; // "compito", "interrogazione", etc.
  weight?: number;
  notes?: string;
}

export interface Lesson {
  id: string;
  subject: string;
  date: string;
  time: string;
  duration: number;
  topic?: string;
}

export interface Homework {
  id: string;
  subject: string;
  description: string;
  dueDate: string;
  notes?: string;
  attachments?: string[];
}

export interface Absence {
  id: string;
  date: string;
  type: "assenza" | "ritardo" | "uscita";
  hours?: number;
  justified: boolean;
  justificationDate?: string;
  justificationReason?: string;
}

export interface Communication {
  id: string;
  title: string;
  content: string;
  sender: string;
  date: string;
  read: boolean;
  attachments?: string[];
}

export interface ApiError {
  code: string;
  message: string;
  details?: unknown;
}

/**
 * Client per l'API di Classeviva
 * Gestisce autenticazione, caricamento dati e gestione errori
 */
export class ClassevivaClient {
  private apiClient: AxiosInstance;
  private token: string | null = null;
  private studentId: string | null = null;

  constructor() {
    this.apiClient = axios.create({
      baseURL: API_BASE_URL,
      timeout: 10000,
      headers: {
        "User-Agent": "ClassevivaExpressive/1.0",
        "Content-Type": "application/json",
        "Z-Dev-Apikey": API_KEY,
      },
    });

    // Interceptor per aggiungere token alle richieste
    this.apiClient.interceptors.request.use((config) => {
      if (this.token) {
        config.headers.Authorization = `Bearer ${this.token}`;
      }
      return config;
    });

    // Interceptor per gestire errori
    this.apiClient.interceptors.response.use(
      (response) => response,
      (error) => {
        if (error.response?.status === 401) {
          this.token = null;
          this.studentId = null;
        }
        return Promise.reject(error);
      }
    );
  }

  /**
   * Autentica l'utente con le credenziali Classeviva
   */
  async login(credentials: LoginCredentials): Promise<AuthToken> {
    try {
      const response = await this.apiClient.post("/auth/login", {
        ident: credentials.username,
        pass: credentials.password,
      });

      const { token } = response.data;
      if (!token) {
        throw new Error("Token non ricevuto dal server");
      }

      this.token = token;

      // Estrai l'ID studente dal token o dalla risposta
      // In base alla struttura dell'API, potrebbe essere necessario fare una richiesta aggiuntiva
      const profile = await this.getProfile();
      this.studentId = profile.id;

      return {
        token,
        expiresAt: Date.now() + 3 * 60 * 60 * 1000, // 3 ore
      };
    } catch (error) {
      throw this.handleError(error, "Errore durante il login");
    }
  }

  /**
   * Effettua il logout
   */
  logout(): void {
    this.token = null;
    this.studentId = null;
  }

  /**
   * Verifica se l'utente è autenticato
   */
  isAuthenticated(): boolean {
    return this.token !== null && this.studentId !== null;
  }

  /**
   * Imposta il token manualmente (per il ripristino da storage)
   */
  setToken(token: string, studentId: string): void {
    this.token = token;
    this.studentId = studentId;
  }

  /**
   * Ottiene il profilo dello studente
   */
  async getProfile(): Promise<StudentProfile> {
    try {
      const response = await this.apiClient.get("/students/me");
      return this.mapProfile(response.data);
    } catch (error) {
      throw this.handleError(error, "Errore nel caricamento del profilo");
    }
  }

  /**
   * Ottiene i voti dello studente
   */
  async getGrades(): Promise<Grade[]> {
    try {
      if (!this.studentId) {
        throw new Error("Student ID non disponibile");
      }

      const response = await this.apiClient.get(
        `/students/${this.studentId}/grades`
      );
      return Array.isArray(response.data)
        ? response.data.map((g) => this.mapGrade(g))
        : [];
    } catch (error) {
      throw this.handleError(error, "Errore nel caricamento dei voti");
    }
  }

  /**
   * Ottiene le lezioni dello studente
   */
  async getLessons(startDate?: string, endDate?: string): Promise<Lesson[]> {
    try {
      if (!this.studentId) {
        throw new Error("Student ID non disponibile");
      }

      const start = startDate || new Date().toISOString().split("T")[0];
      const end =
        endDate ||
        new Date(Date.now() + 30 * 24 * 60 * 60 * 1000)
          .toISOString()
          .split("T")[0];

      const response = await this.apiClient.get(
        `/students/${this.studentId}/lessons/${start}/${end}`
      );
      return Array.isArray(response.data)
        ? response.data.map((l) => this.mapLesson(l))
        : [];
    } catch (error) {
      throw this.handleError(error, "Errore nel caricamento delle lezioni");
    }
  }

  /**
   * Ottiene i compiti dello studente
   */
  async getHomeworks(): Promise<Homework[]> {
    try {
      if (!this.studentId) {
        throw new Error("Student ID non disponibile");
      }

      const response = await this.apiClient.get(
        `/students/${this.studentId}/homeworks`
      );
      return Array.isArray(response.data)
        ? response.data.map((h) => this.mapHomework(h))
        : [];
    } catch (error) {
      throw this.handleError(error, "Errore nel caricamento dei compiti");
    }
  }

  /**
   * Ottiene le assenze dello studente
   */
  async getAbsences(): Promise<Absence[]> {
    try {
      if (!this.studentId) {
        throw new Error("Student ID non disponibile");
      }

      const response = await this.apiClient.get(
        `/students/${this.studentId}/absences`
      );
      return Array.isArray(response.data)
        ? response.data.map((a) => this.mapAbsence(a))
        : [];
    } catch (error) {
      throw this.handleError(error, "Errore nel caricamento delle assenze");
    }
  }

  /**
   * Ottiene le comunicazioni dello studente
   */
  async getCommunications(): Promise<Communication[]> {
    try {
      if (!this.studentId) {
        throw new Error("Student ID non disponibile");
      }

      const response = await this.apiClient.get(
        `/students/${this.studentId}/communications`
      );
      return Array.isArray(response.data)
        ? response.data.map((c) => this.mapCommunication(c))
        : [];
    } catch (error) {
      throw this.handleError(
        error,
        "Errore nel caricamento delle comunicazioni"
      );
    }
  }

  /**
   * Mapper: Converte la risposta API al formato StudentProfile
   */
  private mapProfile(data: any): StudentProfile {
    return {
      id: data.id || data.ident || "",
      name: data.firstName || data.name || "",
      surname: data.lastName || data.surname || "",
      email: data.email || "",
      class: data.classe?.desc || data.class || "",
      section: data.classe?.sezione || data.section || "",
      school: data.scuola?.desc || data.school || "",
      schoolYear: data.anno || new Date().getFullYear().toString(),
    };
  }

  /**
   * Mapper: Converte la risposta API al formato Grade
   */
  private mapGrade(data: any): Grade {
    return {
      id: data.id || data.evtId || "",
      subject: data.materia?.desc || data.subject || "",
      grade: data.votoDecimale || data.grade || "",
      description: data.descrVoto || data.description || "",
      date: data.dataRegistrazione || data.date || "",
      type: data.tipo?.desc || data.type || "",
      weight: data.peso || undefined,
      notes: data.note || undefined,
    };
  }

  /**
   * Mapper: Converte la risposta API al formato Lesson
   */
  private mapLesson(data: any): Lesson {
    return {
      id: data.id || data.lessonId || "",
      subject: data.materia?.desc || data.subject || "",
      date: data.data || data.date || "",
      time: data.ora || data.time || "",
      duration: data.durata || 60,
      topic: data.argomento || data.topic || undefined,
    };
  }

  /**
   * Mapper: Converte la risposta API al formato Homework
   */
  private mapHomework(data: any): Homework {
    return {
      id: data.id || data.hwId || "",
      subject: data.materia?.desc || data.subject || "",
      description: data.contenuto || data.description || "",
      dueDate: data.dataConsegna || data.dueDate || "",
      notes: data.note || undefined,
      attachments: data.allegati || [],
    };
  }

  /**
   * Mapper: Converte la risposta API al formato Absence
   */
  private mapAbsence(data: any): Absence {
    return {
      id: data.id || data.absenceId || "",
      date: data.data || data.date || "",
      type: (data.tipo?.toLowerCase() || "assenza") as "assenza" | "ritardo" | "uscita",
      hours: data.ore || undefined,
      justified: data.giustificata || false,
      justificationDate: data.dataGiustificazione || undefined,
      justificationReason: data.motivoGiustificazione || undefined,
    };
  }

  /**
   * Mapper: Converte la risposta API al formato Communication
   */
  private mapCommunication(data: any): Communication {
    return {
      id: data.id || data.commId || "",
      title: data.titolo || data.title || "",
      content: data.testo || data.content || "",
      sender: data.mittente || data.sender || "",
      date: data.data || data.date || "",
      read: data.letto || false,
      attachments: data.allegati || [],
    };
  }

  /**
   * Gestione centralizzata degli errori
   */
  private handleError(error: any, defaultMessage: string): ApiError {
    if (error.response) {
      const status = error.response.status;
      const data = error.response.data;

      if (status === 401) {
        return {
          code: "UNAUTHORIZED",
          message: "Credenziali non valide. Riprova.",
          details: data,
        };
      }

      if (status === 403) {
        return {
          code: "FORBIDDEN",
          message: "Non hai i permessi per accedere a questa risorsa.",
          details: data,
        };
      }

      if (status === 404) {
        return {
          code: "NOT_FOUND",
          message: "Risorsa non trovata.",
          details: data,
        };
      }

      if (status === 429) {
        return {
          code: "RATE_LIMITED",
          message: "Troppi tentativi. Riprova più tardi.",
          details: data,
        };
      }

      if (status >= 500) {
        return {
          code: "SERVER_ERROR",
          message:
            "Errore del server. Riprova più tardi.",
          details: data,
        };
      }
    }

    if (error.request) {
      return {
        code: "NETWORK_ERROR",
        message: "Errore di connessione. Verifica la tua connessione internet.",
        details: error.message,
      };
    }

    return {
      code: "UNKNOWN_ERROR",
      message: defaultMessage,
      details: error.message,
    };
  }
}

// Singleton instance
export const classeviva = new ClassevivaClient();

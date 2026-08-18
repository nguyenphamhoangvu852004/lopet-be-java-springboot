# Lopet — Pet Social Network

**Lopet** is a social networking platform designed specifically for pet lovers.

The idea behind Lopet is simple: **pets are not just information belonging to their owners — they can also be the center of social interaction.**

Traditional social networks are primarily built around people. A pet may appear in a user's profile or in a post, but the pet itself is usually not treated as an important entity in the social graph.

Lopet explores a different approach.

Instead of making the user the only center of the platform, Lopet allows users to build their social experience around their pets — sharing their lives, connecting with other pet lovers, discovering content, and interacting through their pets.

---

## The Idea

People who love pets often share a significant part of their daily lives with them.

Their pets have:

* Their own names and identities
* Their own stories and personalities
* Photos and memories
* Relationships with other pets
* Activities and experiences worth sharing

However, most social platforms still treat pets mainly as **content attached to a human account**.

Lopet was created to explore the idea of making the pet a more meaningful part of the platform.

The long-term direction is to build a social environment where:

> **People connect with people through their pets.**

For example, instead of simply following another user, you could discover and interact with their pets, follow their activities, share experiences, find other pet lovers with similar interests, or build communities around pets.

---

## Why Build It?

Lopet started as a personal project to explore what a **pet-centered social network** could look like from both a product and engineering perspective.

The project is intentionally developed as a real application rather than a collection of isolated demonstrations.

That means the project involves real problems such as:

* Designing a social graph
* Managing relationships between users and pets
* Deciding who can access or interact with content
* Handling authentication and authorization
* Managing realtime communication
* Designing a relational data model
* Supporting media and social content
* Deploying and running the application outside a local development environment

This makes Lopet a practical environment for experimenting with both **product ideas and backend engineering decisions**.

---

## Core Features

Lopet currently includes several core social networking modules:

* **Account & Authentication** — account management, login and authentication
* **Profile & Pet** — user profiles and pet management
* **Post & Comment** — sharing and interacting with social content
* **Friendship** — relationships between users
* **Group** — community-based interaction
* **Message** — realtime communication
* **Notification** — user activity notifications
* **Report** — reporting inappropriate content
* **Advertisement** — advertising and advertiser management
* **Role & Permission** — authorization and access control

The project is still under active development, so some planned features and improvements have not been implemented yet.

---

## Technology

The current backend is implemented with:

* **Java 21**
* **Spring Boot**
* **Spring Data JPA / Hibernate**
* **MySQL**
* **Redis**
* **Spring Security + JWT**
* **Socket.IO**
* **Cloudinary**
* **Docker**

The frontend is built with **React**.

The current backend in this repository is a Java/Spring Boot migration of the original TypeScript/ExpressJS backend.

The migration was done with the goal of keeping the existing product behavior and database structure while moving the backend to the Java/Spring Boot ecosystem.

---

## A Project That Is Still Evolving

Lopet is not intended to be presented as a finished social network.

It is an **ongoing personal project**.

There are still many ideas and features that I want to explore, and the current implementation represents only the parts that have been built so far.

This is intentional.

The project gives me a place to continuously:

* Explore new product ideas
* Improve the domain model
* Refactor existing implementations
* Experiment with backend architecture
* Learn from real deployment problems
* Add and refine social features

Rather than trying to build everything at once, Lopet is being developed incrementally as the product idea becomes clearer.

---

## Current Direction

The current direction of Lopet is to make **pets a first-class part of the social experience**.

One example is the ongoing evolution of the domain model toward allowing a single account to manage multiple profiles while keeping social content associated with the appropriate pet.

This direction is intended to make the relationship between:

```text
User
  │
  ├── Profile
  │
  ├── Pet
  │    ├── Posts
  │    └── Social activity
  │
  └── Relationships
```

more meaningful than simply treating pets as fields attached to a user.

The goal is not only to build another CRUD application with social features, but to gradually explore what a **pet-first social network** could become.

---

## Demo

The current application is available at:

**Website:**
https://lopet-fe-reactjs-taupe.vercel.app/

You can register a new account or use the following demo accounts:

| Account | Username     | Password         |
| ------- | ------------ | ---------------- |
| Demo 1  | `accountno1` | `LopetPass!2004` |
| Demo 2  | `accountno2` | `LopetPass!2004` |
| Demo 3  | `accountno3` | `LopetPass!2004` |

> The demo represents the current development state of the project. Some features are still under development.

---

## Repository

**Backend:**
https://github.com/nguyenphamhoangvu852004/lopet-be-java-springboot

**Frontend:**
https://github.com/nguyenphamhoangvu852004/lopet-fe-reactjs

---

## Project Status

**Active Development**

Lopet is an ongoing personal project. The current version provides the foundation for the social network, while additional features and improvements are continuously being developed.

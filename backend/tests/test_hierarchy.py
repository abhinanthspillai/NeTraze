import uuid
import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.core.database import SessionLocal
from app.models.domain import User, Project, ProjectMember, Building, Floor, SurveyArea
from app.core.security import get_password_hash, create_access_token

client = TestClient(app)


@pytest.fixture
def db_session():
    session = SessionLocal()
    try:
        yield session
    finally:
        session.rollback()
        session.close()


@pytest.fixture
def owner_admin(db_session):
    user = User(
        id=uuid.uuid4(),
        email=f"owner_admin_{uuid.uuid4().hex[:6]}@netraze.app",
        password_hash=get_password_hash("Password123!"),
        role="administrator"
    )
    db_session.add(user)
    db_session.commit()
    return user


@pytest.fixture
def member_admin(db_session):
    user = User(
        id=uuid.uuid4(),
        email=f"member_admin_{uuid.uuid4().hex[:6]}@netraze.app",
        password_hash=get_password_hash("Password123!"),
        role="administrator"
    )
    db_session.add(user)
    db_session.commit()
    return user


@pytest.fixture
def member_tech(db_session):
    user = User(
        id=uuid.uuid4(),
        email=f"member_tech_{uuid.uuid4().hex[:6]}@netraze.app",
        password_hash=get_password_hash("Password123!"),
        role="user"
    )
    db_session.add(user)
    db_session.commit()
    return user


@pytest.fixture
def non_member(db_session):
    user = User(
        id=uuid.uuid4(),
        email=f"non_member_{uuid.uuid4().hex[:6]}@netraze.app",
        password_hash=get_password_hash("Password123!"),
        role="user"
    )
    db_session.add(user)
    db_session.commit()
    return user


def auth_headers(user):
    token = create_access_token(subject=user.id)
    return {"Authorization": f"Bearer {token}"}


# ==========================================
# TEST PROJECT CREATION & AUTHORIZATION
# ==========================================

def test_administrator_creates_project(owner_admin):
    response = client.post(
        "/api/v1/projects",
        json={"name": "HQ Campus Survey"},
        headers=auth_headers(owner_admin)
    )
    assert response.status_code == 201
    data = response.json()
    assert data["name"] == "HQ Campus Survey"
    assert data["owner_id"] == str(owner_admin.id)
    assert data["is_active"] is True


def test_user_can_create_owned_project(member_tech):
    response = client.post(
        "/api/v1/projects",
        json={"name": "User Owned Survey Project"},
        headers=auth_headers(member_tech)
    )
    assert response.status_code == 201
    data = response.json()
    assert data["name"] == "User Owned Survey Project"
    assert data["owner_id"] == str(member_tech.id)


def test_user_can_create_hierarchy_and_survey_inside_owned_project(db_session, member_tech):
    res_project = client.post(
        "/api/v1/projects",
        json={"name": "Self Service Survey Project"},
        headers=auth_headers(member_tech)
    )
    assert res_project.status_code == 201
    project_id = res_project.json()["id"]
    assert res_project.json()["owner_id"] == str(member_tech.id)

    membership = db_session.get(
        ProjectMember,
        {"project_id": uuid.UUID(project_id), "user_id": member_tech.id}
    )
    assert membership is not None

    res_building = client.post(
        f"/api/v1/projects/{project_id}/buildings",
        json={"name": "MCA Block"},
        headers=auth_headers(member_tech)
    )
    assert res_building.status_code == 201
    building_id = res_building.json()["id"]

    res_floor = client.post(
        f"/api/v1/buildings/{building_id}/floors",
        json={"name": "Second Floor"},
        headers=auth_headers(member_tech)
    )
    assert res_floor.status_code == 201
    floor_id = res_floor.json()["id"]

    res_area = client.post(
        f"/api/v1/floors/{floor_id}/survey-areas",
        json={"name": "Lab"},
        headers=auth_headers(member_tech)
    )
    assert res_area.status_code == 201
    assert res_area.json()["name"] == "Lab"
    survey_area_id = res_area.json()["id"]

    survey_id = str(uuid.uuid4())
    res_survey = client.post(
        f"/api/v1/survey-areas/{survey_area_id}/surveys",
        json={
            "id": survey_id,
            "title": "Self Service Survey",
            "mode": "location_survey"
        },
        headers=auth_headers(member_tech)
    )
    assert res_survey.status_code == 201
    assert res_survey.json()["id"] == survey_id


def test_unrelated_user_cannot_create_survey_in_another_users_project(
    db_session, member_tech, non_member
):
    res_project = client.post(
        "/api/v1/projects",
        json={"name": "Owner Only Survey Project"},
        headers=auth_headers(member_tech)
    )
    project_id = uuid.UUID(res_project.json()["id"])

    building = Building(id=uuid.uuid4(), project_id=project_id, name="Owner Building")
    floor = Floor(id=uuid.uuid4(), building_id=building.id, name="Owner Floor")
    area = SurveyArea(id=uuid.uuid4(), floor_id=floor.id, name="Owner Area")
    db_session.add_all([building, floor, area])
    db_session.commit()

    res_survey = client.post(
        f"/api/v1/survey-areas/{area.id}/surveys",
        json={
            "id": str(uuid.uuid4()),
            "title": "Unauthorized Survey",
            "mode": "location_survey"
        },
        headers=auth_headers(non_member)
    )
    assert res_survey.status_code == 404


def test_retry_reuses_existing_owned_hierarchy_rows(db_session, member_tech):
    headers = auth_headers(member_tech)
    names = {
        "project": "Retry Safe Project",
        "building": "Retry Safe Building",
        "floor": "Retry Safe Floor",
        "area": "Retry Safe Area",
    }

    def create_chain():
        project = client.post("/api/v1/projects", json={"name": names["project"]}, headers=headers).json()
        building = client.post(
            f"/api/v1/projects/{project['id']}/buildings",
            json={"name": names["building"]},
            headers=headers
        ).json()
        floor = client.post(
            f"/api/v1/buildings/{building['id']}/floors",
            json={"name": names["floor"]},
            headers=headers
        ).json()
        area = client.post(
            f"/api/v1/floors/{floor['id']}/survey-areas",
            json={"name": names["area"]},
            headers=headers
        ).json()
        return project, building, floor, area

    first = create_chain()
    second = create_chain()

    assert [item["id"] for item in first] == [item["id"] for item in second]
    assert db_session.query(Project).filter_by(owner_id=member_tech.id, name=names["project"]).count() == 1
    assert db_session.query(Building).filter_by(project_id=uuid.UUID(first[0]["id"]), name=names["building"]).count() == 1
    assert db_session.query(Floor).filter_by(building_id=uuid.UUID(first[1]["id"]), name=names["floor"]).count() == 1
    assert db_session.query(SurveyArea).filter_by(floor_id=uuid.UUID(first[2]["id"]), name=names["area"]).count() == 1


# ==========================================
# TEST HIERARCHY TREE MUTATION & ACCESS
# ==========================================

def test_full_hierarchy_lifecycle_and_permissions(
    db_session, owner_admin, member_admin, member_tech, non_member
):
    # 1. Owner Admin creates project
    res_proj = client.post(
        "/api/v1/projects",
        json={"name": "Main Building Alpha"},
        headers=auth_headers(owner_admin)
    )
    assert res_proj.status_code == 201
    project_id = res_proj.json()["id"]

    # Add member_admin and member_tech to project_members
    db_session.add(ProjectMember(project_id=uuid.UUID(project_id), user_id=member_admin.id))
    db_session.add(ProjectMember(project_id=uuid.UUID(project_id), user_id=member_tech.id))
    db_session.commit()

    # 2. GET /projects listing checks
    res_list_owner = client.get("/api/v1/projects", headers=auth_headers(owner_admin))
    assert any(p["id"] == project_id for p in res_list_owner.json())

    res_list_member_admin = client.get("/api/v1/projects", headers=auth_headers(member_admin))
    assert any(p["id"] == project_id for p in res_list_member_admin.json())

    res_list_tech = client.get("/api/v1/projects", headers=auth_headers(member_tech))
    assert any(p["id"] == project_id for p in res_list_tech.json())

    res_list_non_member = client.get("/api/v1/projects", headers=auth_headers(non_member))
    assert not any(p["id"] == project_id for p in res_list_non_member.json())

    # 3. Building creation
    # Non-member -> 404
    res_b_non = client.post(
        f"/api/v1/projects/{project_id}/buildings",
        json={"name": "Building 1"},
        headers=auth_headers(non_member)
    )
    assert res_b_non.status_code == 404

    # Member Tech -> 403
    res_b_tech = client.post(
        f"/api/v1/projects/{project_id}/buildings",
        json={"name": "Building 1"},
        headers=auth_headers(member_tech)
    )
    assert res_b_tech.status_code == 403

    # Member Admin (not owner) -> 403
    res_b_madmin = client.post(
        f"/api/v1/projects/{project_id}/buildings",
        json={"name": "Building 1"},
        headers=auth_headers(member_admin)
    )
    assert res_b_madmin.status_code == 403

    # Owner Admin -> 201 Created
    res_b_owner = client.post(
        f"/api/v1/projects/{project_id}/buildings",
        json={"name": "Engineering Block"},
        headers=auth_headers(owner_admin)
    )
    assert res_b_owner.status_code == 201
    building_id = res_b_owner.json()["id"]

    # 4. Floor creation
    res_f_owner = client.post(
        f"/api/v1/buildings/{building_id}/floors",
        json={"name": "Floor 2"},
        headers=auth_headers(owner_admin)
    )
    assert res_f_owner.status_code == 201
    floor_id = res_f_owner.json()["id"]

    # 5. Survey Area creation
    res_sa_owner = client.post(
        f"/api/v1/floors/{floor_id}/survey-areas",
        json={"name": "Server Room West"},
        headers=auth_headers(owner_admin)
    )
    assert res_sa_owner.status_code == 201
    survey_area_id = res_sa_owner.json()["id"]

    # 6. Read Survey Area
    # Member tech -> 200 OK
    res_sa_get_tech = client.get(f"/api/v1/survey-areas/{survey_area_id}", headers=auth_headers(member_tech))
    assert res_sa_get_tech.status_code == 200
    assert res_sa_get_tech.json()["name"] == "Server Room West"

    # Non-member -> 404 Not Found
    res_sa_get_non = client.get(f"/api/v1/survey-areas/{survey_area_id}", headers=auth_headers(non_member))
    assert res_sa_get_non.status_code == 404

    # 7. Patch Survey Area
    # Member tech -> 403
    res_sa_patch_tech = client.patch(
        f"/api/v1/survey-areas/{survey_area_id}",
        json={"name": "Hacked Area"},
        headers=auth_headers(member_tech)
    )
    assert res_sa_patch_tech.status_code == 403

    # Member admin -> 403
    res_sa_patch_madmin = client.patch(
        f"/api/v1/survey-areas/{survey_area_id}",
        json={"name": "Hacked Area"},
        headers=auth_headers(member_admin)
    )
    assert res_sa_patch_madmin.status_code == 403

    # Owner admin -> 200 OK
    res_sa_patch_owner = client.patch(
        f"/api/v1/survey-areas/{survey_area_id}",
        json={"name": "Server Room West - Updated"},
        headers=auth_headers(owner_admin)
    )
    assert res_sa_patch_owner.status_code == 200
    assert res_sa_patch_owner.json()["name"] == "Server Room West - Updated"

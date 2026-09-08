import uuid
import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.core.database import SessionLocal
from app.core.security import create_access_token, get_password_hash
from app.models.domain import Building, Floor, FloorPlan, Project, ProjectMember, SimpleMap, SurveyArea, User

client = TestClient(app)


def setup_survey_test_data():
    session = SessionLocal()
    try:
        admin_id = uuid.uuid4()
        admin = User(
            id=admin_id,
            email=f"admin_{admin_id.hex[:6]}@netraze.app",
            password_hash=get_password_hash("Password123!"),
            role="administrator"
        )
        tech_id = uuid.uuid4()
        tech = User(
            id=tech_id,
            email=f"tech_{tech_id.hex[:6]}@netraze.app",
            password_hash=get_password_hash("Password123!"),
            role="user"
        )
        session.add_all([admin, tech])
        session.commit()

        project_id = uuid.uuid4()
        project = Project(id=project_id, owner_id=admin_id, name="Test Survey Project", is_active=True)
        session.add(project)
        session.commit()

        building_id = uuid.uuid4()
        building = Building(id=building_id, project_id=project_id, name="Building 101")
        session.add(building)
        session.commit()

        floor_id = uuid.uuid4()
        floor = Floor(id=floor_id, building_id=building_id, name="Floor 3")
        session.add(floor)
        session.commit()

        area_id = uuid.uuid4()
        area = SurveyArea(id=area_id, floor_id=floor_id, name="Lab 305")
        session.add(area)
        session.commit()

        floor_plan_id = uuid.uuid4()
        floor_plan = FloorPlan(id=floor_plan_id, survey_area_id=area_id, storage_path="/plans/test.png", uploaded_by=admin_id)
        session.add(floor_plan)

        simple_map_id = uuid.uuid4()
        simple_map = SimpleMap(id=simple_map_id, survey_area_id=area_id, created_by=admin_id)
        session.add(simple_map)
        session.commit()

        pm_admin = ProjectMember(project_id=project_id, user_id=admin_id)
        pm_tech = ProjectMember(project_id=project_id, user_id=tech_id)
        session.add_all([pm_admin, pm_tech])
        session.commit()

        token_admin = create_access_token(admin_id)
        token_tech = create_access_token(tech_id)

        return {
            "area_id": str(area_id),
            "floor_plan_id": str(floor_plan_id),
            "simple_map_id": str(simple_map_id),
            "token_admin": token_admin,
            "token_tech": token_tech
        }
    finally:
        session.close()


def test_create_survey_preserves_android_uuid():
    data = setup_survey_test_data()
    headers = {"Authorization": f"Bearer {data['token_tech']}"}
    android_uuid = str(uuid.uuid4())

    payload = {
        "id": android_uuid,
        "title": "Offline-First Location Survey",
        "mode": "location_survey"
    }

    res = client.post(f"/api/v1/survey-areas/{data['area_id']}/surveys", json=payload, headers=headers)
    assert res.status_code == 201
    res_data = res.json()
    assert res_data["id"] == android_uuid
    assert res_data["title"] == "Offline-First Location Survey"
    assert res_data["mode"] == "location_survey"
    assert res_data["status"] == "in_progress"


def test_user_owned_hierarchy_can_create_location_survey():
    user_id = uuid.uuid4()
    user = User(
        id=user_id,
        email=f"owner_user_{user_id.hex[:6]}@netraze.app",
        password_hash=get_password_hash("Password123!"),
        role="user"
    )
    session = SessionLocal()
    try:
        session.add(user)
        session.commit()
    finally:
        session.close()

    headers = {"Authorization": f"Bearer {create_access_token(user_id)}"}

    res_project = client.post(
        "/api/v1/projects",
        json={"name": "Device Created Survey Project"},
        headers=headers
    )
    assert res_project.status_code == 201
    project_id = uuid.UUID(res_project.json()["id"])
    assert res_project.json()["owner_id"] == str(user_id)

    session = SessionLocal()
    try:
        owner_member = session.get(ProjectMember, {"project_id": project_id, "user_id": user_id})
        assert owner_member is not None
    finally:
        session.close()

    res_building = client.post(
        f"/api/v1/projects/{project_id}/buildings",
        json={"name": "MCA Block"},
        headers=headers
    )
    assert res_building.status_code == 201
    building_id = res_building.json()["id"]

    res_floor = client.post(
        f"/api/v1/buildings/{building_id}/floors",
        json={"name": "Second Floor"},
        headers=headers
    )
    assert res_floor.status_code == 201
    floor_id = res_floor.json()["id"]

    res_area = client.post(
        f"/api/v1/floors/{floor_id}/survey-areas",
        json={"name": "Lab 204"},
        headers=headers
    )
    assert res_area.status_code == 201
    area_id = res_area.json()["id"]

    android_uuid = str(uuid.uuid4())
    res_survey = client.post(
        f"/api/v1/survey-areas/{area_id}/surveys",
        json={
            "id": android_uuid,
            "title": "Physical Device Location Survey",
            "mode": "location_survey"
        },
        headers=headers
    )
    assert res_survey.status_code == 201
    assert res_survey.json()["id"] == android_uuid
    assert res_survey.json()["mode"] == "location_survey"


def test_get_surveys_and_survey_by_id():
    data = setup_survey_test_data()
    headers = {"Authorization": f"Bearer {data['token_admin']}"}
    android_uuid = str(uuid.uuid4())

    client.post(
        f"/api/v1/survey-areas/{data['area_id']}/surveys",
        json={
            "id": android_uuid,
            "title": "Survey For Retrieval",
            "mode": "simple_map",
            "simple_map_id": data["simple_map_id"]
        },
        headers=headers
    )

    res_list = client.get(f"/api/v1/survey-areas/{data['area_id']}/surveys", headers=headers)
    assert res_list.status_code == 200
    surveys = res_list.json()
    assert len(surveys) >= 1

    res_single = client.get(f"/api/v1/surveys/{android_uuid}", headers=headers)
    assert res_single.status_code == 200
    assert res_single.json()["id"] == android_uuid


def test_patch_survey_title_only_and_reject_prohibited_fields():
    data = setup_survey_test_data()
    headers = {"Authorization": f"Bearer {data['token_admin']}"}
    android_uuid = str(uuid.uuid4())

    client.post(
        f"/api/v1/survey-areas/{data['area_id']}/surveys",
        json={"id": android_uuid, "title": "Original Title", "mode": "location_survey"},
        headers=headers
    )

    # Valid PATCH title
    res_patch = client.patch(f"/api/v1/surveys/{android_uuid}", json={"title": "Updated Title"}, headers=headers)
    assert res_patch.status_code == 200
    assert res_patch.json()["title"] == "Updated Title"

    # Prohibited fields -> 422 Unprocessable Entity
    prohibited_payloads = [
        {"status": "completed"},
        {"completed_at": "2026-08-19T00:00:00Z"},
        {"mode": "simple_map"},
        {"floor_plan_id": str(uuid.uuid4())},
        {"simple_map_id": str(uuid.uuid4())},
        {"created_by": str(uuid.uuid4())},
        {"id": str(uuid.uuid4())},
        {"notes": "Illegal note"}
    ]

    for payload in prohibited_payloads:
        res = client.patch(f"/api/v1/surveys/{android_uuid}", json=payload, headers=headers)
        assert res.status_code == 422, f"Payload {payload} should be rejected with 422, got {res.status_code}"

    # Verify survey metadata in DB remains unchanged except title
    res_check = client.get(f"/api/v1/surveys/{android_uuid}", headers=headers)
    assert res_check.status_code == 200
    survey_data = res_check.json()
    assert survey_data["title"] == "Updated Title"
    assert survey_data["status"] == "in_progress"
    assert survey_data["mode"] == "location_survey"
    assert survey_data["completed_at"] is None
